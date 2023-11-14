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

	replicaSet := env.MongoReplicaSet()
	if len(replicaSet) > 0 {
		connectionString.WriteString("&replicaSet=")
		connectionString.WriteString(replicaSet)
	}

	return connectionString.String()
}

func collection(collectionName string) *mongo.Collection {
	mongoOptions := options.Client().ApplyURI(ConnectionString())
	client, err := mongo.Connect(context.Background(), mongoOptions)
	if err != nil {
		logrus.Error("mongo client failed")
		logger.LogAndPrintError(err)
	}
	collection := client.Database(env.MongoValues.Database).Collection(collectionName)

	return collection
}

func DatasetsCollection() *mongo.Collection {
	return collection(env.MongoValues.DatasetsCollection)
}
