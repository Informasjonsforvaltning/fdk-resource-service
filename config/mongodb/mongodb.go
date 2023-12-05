package mongodb

import (
	"context"
	"strings"

	"github.com/sirupsen/logrus"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/env"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
)

func ConnectionString() string {
	var connectionString strings.Builder
	connectionString.WriteString("mongodb://")
	connectionString.WriteString(env.MongoUsername())
	connectionString.WriteString(":")
	connectionString.WriteString(env.MongoPassword())
	connectionString.WriteString("@")
	connectionString.WriteString(env.MongoHost())
	connectionString.WriteString("/")
	connectionString.WriteString(env.MongoValues.Database)
	connectionString.WriteString("?authSource=")
	connectionString.WriteString(env.MongoAuthSource())
	connectionString.WriteString("&replicaSet=")
	connectionString.WriteString(env.MongoReplicaSet())

	return connectionString.String()
}

func Client() *mongo.Client {
	mongoOptions := options.Client().ApplyURI(ConnectionString())
	client, err := mongo.Connect(context.Background(), mongoOptions)
	if err != nil {
		logrus.Error("mongo client failed")
		logger.LogAndPrintError(err)
	}

	return client
}

func Collection(client *mongo.Client, collectionName string) *mongo.Collection {
	return client.Database(env.MongoValues.Database).Collection(collectionName)
}
