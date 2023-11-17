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

type DatasetRepository interface {
	StoreDatasets(ctx context.Context, datasets []model.DatasetDBO) error
	GetDatasets(ctx context.Context, query bson.D) ([]model.DatasetDBO, error)
	GetDataset(ctx context.Context, id string) (model.DatasetDBO, error)
}

type DatasetRepositoryImpl struct {
	collection *mongo.Collection
}

var datasetRepository *DatasetRepositoryImpl

func InitRepository() *DatasetRepositoryImpl {
	if datasetRepository == nil {
		datasetRepository = &DatasetRepositoryImpl{collection: mongodb.DatasetsCollection()}
	}
	return datasetRepository
}

func (r DatasetRepositoryImpl) StoreDatasets(ctx context.Context, datasets []model.DatasetDBO) error {
	var replaceOptions = options.Replace()
	replaceOptions.Upsert = pointer.Of(true)

	client := mongodb.Client()
	coll := mongodb.Collection(client, env.MongoValues.DatasetsCollection)

	return client.UseSession(ctx, func(sctx mongo.SessionContext) error {
		err := sctx.StartTransaction(options.Transaction().
			SetReadConcern(readconcern.Snapshot()).
			SetWriteConcern(writeconcern.Majority()),
		)
		
		if err != nil {
			return err
		}

		for _, dataset := range datasets {
			_, err := coll.ReplaceOne(sctx, bson.D{{Key: "_id", Value: dataset.ID}}, dataset, replaceOptions)
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

func (r DatasetRepositoryImpl) GetDatasets(ctx context.Context, query bson.D) ([]model.DatasetDBO, error) {
	var datasets []model.DatasetDBO

	current, err := r.collection.Find(ctx, query)
	if err != nil {
		return datasets, err
	}

	for current.Next(ctx) {
		var dataset model.DatasetDBO
		err := bson.Unmarshal(current.Current, &dataset)
		if err != nil {
			return datasets, err
		}
		datasets = append(datasets, dataset)
	}

	if err := current.Err(); err != nil {
		return datasets, err
	}
	defer func(current *mongo.Cursor, ctx context.Context) {
		err := current.Close(ctx)
		if err != nil {
			logger.LogAndPrintError(err)
		}
	}(current, ctx)

	return datasets, nil
}

func (r DatasetRepositoryImpl) GetDataset(ctx context.Context, id string) (model.DatasetDBO, error) {
	filter := bson.D{{Key: "_id", Value: id}}
	bytes, err := r.collection.FindOne(ctx, filter).Raw()

	if err != nil {
		return model.DatasetDBO{}, err
	}

	var dataset model.DatasetDBO
	unmarshalError := bson.Unmarshal(bytes, &dataset)
	if unmarshalError != nil {
		return model.DatasetDBO{}, unmarshalError
	}

	return dataset, nil
}
