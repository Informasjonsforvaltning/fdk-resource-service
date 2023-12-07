package env

import "os"

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}

func ApiKey() string {
	return getEnv("API_KEY", "test")
}

func MongoHost() string {
	return getEnv("MONGO_HOST", "localhost:27017")
}

func MongoPassword() string {
	return getEnv("MONGO_PASSWORD", "admin")
}

func MongoUsername() string {
	return getEnv("MONGO_USERNAME", "root")
}

func MongoAuthSource() string {
	return getEnv("MONGODB_AUTH", "admin")
}

func MongoReplicaSet() string {
	return getEnv("MONGODB_REPLICASET", "replicaset")
}

func KafkaBrokers() string {
	return getEnv("KAFKA_BROKERS", "localhost:9092")
}

func SchemaRegistry() string {
	return getEnv("SCHEMA_REGISTRY", "http://localhost:5050")
}

type MongoConstants struct {
	ConceptsCollection          string
	DataServicesCollection      string
	DatasetsCollection          string
	Database                    string
	EventsCollection            string
	InformationModelsCollection string
}

type KafkaConstants struct {
	ConceptTopic     string
	DataServiceTopic string
	DatasetTopic     string
	EventTopic       string
	InfoModelTopic   string
	ServiceTopic     string
}

type Paths struct {
	Ping              string
	Ready             string
	Concepts          string
	Concept           string
	DataServices      string
	DataService       string
	Datasets          string
	Dataset           string
	Events            string
	Event             string
	InformationModels string
	InformationModel  string
}

var MongoValues = MongoConstants{
	ConceptsCollection:          "concepts",
	DataServicesCollection:      "dataServices",
	DatasetsCollection:          "datasets",
	Database:                    "fdkResourceService",
	EventsCollection:            "events",
	InformationModelsCollection: "informationModels",
}

var KafkaValues = KafkaConstants{
	ConceptTopic:     "concept-events",
	DataServiceTopic: "data-service-events",
	DatasetTopic:     "dataset-events",
	EventTopic:       "event-events",
	InfoModelTopic:   "information-model-events",
	ServiceTopic:     "service-events",
}

var PathValues = Paths{
	Ping:              "/ping",
	Ready:             "/ready",
	Concepts:          "/concepts",
	Concept:           "/concepts/:id",
	DataServices:      "/data-services",
	DataService:       "/data-services/:id",
	Datasets:          "/datasets",
	Dataset:           "/datasets/:id",
	Events:            "/events",
	Event:             "/events/:id",
	InformationModels: "/information-models",
	InformationModel:  "/information-models/:id",
}
