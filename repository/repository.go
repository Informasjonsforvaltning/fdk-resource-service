package repository

import (
	"context"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
	"go.mongodb.org/mongo-driver/mongo/readconcern"
	"go.mongodb.org/mongo-driver/mongo/writeconcern"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/env"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/mongodb"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/pointer"
	"github.com/sirupsen/logrus"
)

type ResourceRepository interface {
	StoreResource(ctx context.Context, resource model.DBO) error
	GetResources(ctx context.Context, query bson.D) ([]model.DBO, error)
	GetResource(ctx context.Context, id string) (model.DBO, error)
}

type ResourceRepositoryImpl struct {
	client     *mongo.Client
	collection *mongo.Collection
}

var conceptRepository *ResourceRepositoryImpl

func InitConceptRepository() *ResourceRepositoryImpl {
	if conceptRepository == nil {
		client := mongodb.Client()
		collection := mongodb.Collection(client, env.MongoValues.ConceptsCollection)
		conceptRepository = &ResourceRepositoryImpl{client: client, collection: collection}
	}
	return conceptRepository
}

var dataServiceRepository *ResourceRepositoryImpl

func InitDataServiceRepository() *ResourceRepositoryImpl {
	if dataServiceRepository == nil {
		client := mongodb.Client()
		collection := mongodb.Collection(client, env.MongoValues.DataServicesCollection)
		dataServiceRepository = &ResourceRepositoryImpl{client: client, collection: collection}
	}
	return dataServiceRepository
}

var datasetRepository *ResourceRepositoryImpl

func InitDatasetRepository() *ResourceRepositoryImpl {
	if datasetRepository == nil {
		client := mongodb.Client()
		collection := mongodb.Collection(client, env.MongoValues.DatasetsCollection)
		datasetRepository = &ResourceRepositoryImpl{client: client, collection: collection}
	}
	return datasetRepository
}

var eventRepository *ResourceRepositoryImpl

func InitEventRepository() *ResourceRepositoryImpl {
	if eventRepository == nil {
		client := mongodb.Client()
		collection := mongodb.Collection(client, env.MongoValues.EventsCollection)
		eventRepository = &ResourceRepositoryImpl{client: client, collection: collection}
	}
	return eventRepository
}

var informationModelRepository *ResourceRepositoryImpl

func InitInformationModelRepository() *ResourceRepositoryImpl {
	if informationModelRepository == nil {
		client := mongodb.Client()
		collection := mongodb.Collection(client, env.MongoValues.InformationModelsCollection)
		informationModelRepository = &ResourceRepositoryImpl{client: client, collection: collection}
	}
	return informationModelRepository
}

var serviceRepository *ResourceRepositoryImpl

func InitServiceRepository() *ResourceRepositoryImpl {
	if serviceRepository == nil {
		client := mongodb.Client()
		collection := mongodb.Collection(client, env.MongoValues.ServicesCollection)
		serviceRepository = &ResourceRepositoryImpl{client: client, collection: collection}
	}
	return serviceRepository
}

func (r ResourceRepositoryImpl) StoreResource(ctx context.Context, resource model.DBO) error {
	var replaceOptions = options.Replace()
	replaceOptions.Upsert = pointer.Of(true)

	return r.client.UseSession(ctx, func(sctx mongo.SessionContext) error {
		err := sctx.StartTransaction(options.Transaction().
			SetReadConcern(readconcern.Snapshot()).
			SetWriteConcern(writeconcern.Majority()),
		)

		if err != nil {
			return err
		}

		_, err = r.collection.ReplaceOne(sctx, bson.D{{Key: "_id", Value: resource.ID}}, resource, replaceOptions)
		if err != nil {
			sctx.AbortTransaction(sctx)
			return err
		}
		for {
			err = sctx.CommitTransaction(sctx)
			switch e := err.(type) {
			case nil:
				return nil
			case mongo.CommandError:
				if e.HasErrorLabel("UnknownTransactionCommitResult") {
					logrus.Info("UnknownTransactionCommitResult, retrying commit operation...")
					continue
				} else {
					return e
				}
			default:
				return e
			}
		}
	})
}

func (r ResourceRepositoryImpl) GetResources(ctx context.Context, query bson.D) ([]model.DBO, error) {
	var resources []model.DBO

	current, err := r.collection.Find(ctx, query)
	if err != nil {
		return resources, err
	}

	for current.Next(ctx) {
		var dbo model.DBO
		err := bson.Unmarshal(current.Current, &dbo)
		if err != nil {
			return resources, err
		}
		resources = append(resources, dbo)
	}

	if err := current.Err(); err != nil {
		return resources, err
	}
	defer func(current *mongo.Cursor, ctx context.Context) {
		err := current.Close(ctx)
		if err != nil {
			logger.LogAndPrintError(err)
		}
	}(current, ctx)

	return resources, nil
}

func (r ResourceRepositoryImpl) GetResource(ctx context.Context, id string) (model.DBO, error) {
	filter := bson.D{{Key: "_id", Value: id}}
	bytes, err := r.collection.FindOne(ctx, filter).Raw()

	if err != nil {
		return model.DBO{}, err
	}

	var dbo model.DBO
	unmarshalError := bson.Unmarshal(bytes, &dbo)
	if unmarshalError != nil {
		return model.DBO{}, unmarshalError
	}

	return dbo, nil
}
