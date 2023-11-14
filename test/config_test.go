package test

import (
	"context"
	"fmt"
	"github.com/ory/dockertest/v3"
	"log"
	"os"
	"testing"

	"github.com/ory/dockertest/v3/docker"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

var dbClient *mongo.Client

func TestMain(m *testing.M) {
	MongoContainerRunner(m)
}

func MongoContainerRunner(m *testing.M) {
	pool, err := dockertest.NewPool("")
	if err != nil {
		log.Fatalf("Could not connect to docker: %s", err)
	}

	currentDirectory, err := os.Getwd()
	if err != nil {
		log.Fatalf("Could not get directory: %s", err)
	}

	resource, err := pool.RunWithOptions(&dockertest.RunOptions{
		Repository: "mongo",
		Tag:        "4",
		Env: []string{
			"MONGO_INITDB_ROOT_USERNAME=admin",
			"MONGO_INITDB_ROOT_PASSWORD=admin",
		},
		Mounts: []string{
			currentDirectory + "/init-mongo:/docker-entrypoint-initdb.d",
		},
	}, func(config *docker.HostConfig) {
		// set AutoRemove to true so that stopped container is automatically removed
		config.AutoRemove = true
		config.RestartPolicy = docker.RestartPolicy{
			Name: "no",
		}
	})
	if err != nil {
		log.Fatalf("Could not start resource: %s", err)
	}

	// set MONGO_HOST environment variable to reference test db
	os.Setenv("MONGO_HOST", fmt.Sprintf("localhost:%s", resource.GetPort("27017/tcp")))

	// exponential backoff-retry, because the application in the container might not be ready to accept connections yet
	err = pool.Retry(func() error {
		var err error
		dbClient, err = mongo.Connect(
			context.TODO(),
			options.Client().ApplyURI(
				fmt.Sprintf("mongodb://admin:admin@localhost:%s", resource.GetPort("27017/tcp")),
			),
		)
		if err != nil {
			return err
		}
		return dbClient.Ping(context.TODO(), nil)
	})

	if err != nil {
		log.Fatalf("Could not connect to docker: %s", err)
	}

	// run tests
	code := m.Run()

	// kill and remove the container
	if err = pool.Purge(resource); err != nil {
		log.Fatalf("Could not purge resource: %s", err)
	}

	// disconnect mongodb client
	if err = dbClient.Disconnect(context.TODO()); err != nil {
		panic(err)
	}

	os.Exit(code)
}
