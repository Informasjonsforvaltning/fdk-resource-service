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
	DatasetsCollection string
	Database           string
}

type KafkaConstants struct {
	ConceptTopic   string
	DatasetTopic   string
	InfoModelTopic string
}

type Paths struct {
	Ping     string
	Ready    string
	Datasets string
	Dataset  string
}

var MongoValues = MongoConstants{
	DatasetsCollection: "datasets",
	Database:           "fdkResourceService",
}

var KafkaValues = KafkaConstants{
	ConceptTopic:   "concept-events",
	DatasetTopic:   "dataset-events",
	InfoModelTopic: "information-model-events",
}

var PathValues = Paths{
	Ping:     "/ping",
	Ready:    "/ready",
	Datasets: "/datasets",
	Dataset:  "/datasets/:id",
}
