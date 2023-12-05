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
	StoreResources(ctx context.Context, resources []model.DBO) error
	GetResources(ctx context.Context, query bson.D) ([]model.DBO, error)
	GetResource(ctx context.Context, id string) (model.DBO, error)
}

type ResourceRepositoryImpl struct {
	client     *mongo.Client
	collection *mongo.Collection
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

func (r ResourceRepositoryImpl) StoreResources(ctx context.Context, resources []model.DBO) error {
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

		for _, dbo := range resources {
			_, err := r.collection.ReplaceOne(sctx, bson.D{{Key: "_id", Value: dbo.ID}}, dbo, replaceOptions)
			if err != nil {
				sctx.AbortTransaction(sctx)
				return err
			}
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
