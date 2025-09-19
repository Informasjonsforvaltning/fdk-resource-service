package repository

import (
	"context"
	"errors"

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
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
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
	// ID should already have been sanitized and validated by caller

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

		// Use parameterized query with validated ID
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

	// Validate query to prevent injection attacks
	if err := r.validateQuery(query); err != nil {
		return resources, err
	}

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
	// Validate and sanitize ID to prevent injection
	sanitizedID, err := validate.SanitizeAndValidateID(id)
	if err != nil {
		return model.DBO{}, err
	}

	filter := bson.D{{Key: "_id", Value: sanitizedID}}
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

// validateQuery validates MongoDB queries to prevent injection attacks
func (r ResourceRepositoryImpl) validateQuery(query bson.D) error {
	// Define allowed query operators and fields
	allowedOperators := map[string]bool{
		"$eq": true, "$ne": true, "$gt": true, "$gte": true, "$lt": true, "$lte": true,
		"$in": true, "$nin": true, "$exists": true, "$regex": true, "$and": true, "$or": true,
		"$not": true, "$nor": true, "$all": true, "$elemMatch": true, "$size": true,
	}
	
	allowedFields := map[string]bool{
		"_id": true, "deleted": true, "timestamp": true, "resource": true,
	}

	for _, elem := range query {
		key := elem.Key
		value := elem.Value

		// Check if field is allowed
		if !allowedFields[key] {
			// Check if it's an operator
			if !allowedOperators[key] {
				return errors.New("disallowed field or operator in query: " + key)
			}
		}

		// Recursively validate nested queries
		if nestedQuery, ok := value.(bson.D); ok {
			if err := r.validateQuery(nestedQuery); err != nil {
				return err
			}
		}

		// Validate array of queries (for $and, $or, etc.)
		if nestedQueries, ok := value.([]bson.D); ok {
			for _, nestedQuery := range nestedQueries {
				if err := r.validateQuery(nestedQuery); err != nil {
					return err
				}
			}
		}
	}

	return nil
}
