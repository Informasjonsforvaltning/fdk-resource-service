package env

import (
	"os"
	"strings"
)

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}

func CorsOriginPatterns() []string {
	return strings.Split(getEnv("CORS_ORIGIN_PATTERNS", "*"), ",")
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

func KeycloakHost() string {
	return getEnv("SSO_AUTH_URI", "https://sso.staging.fellesdatakatalog.digdir.no/auth")
}

func SchemaRegistry() string {
	return strings.Split(getEnv("SCHEMA_REGISTRY", "http://localhost:5050"), ",")[0]
}

type MongoConstants struct {
	ConceptsCollection          string
	DataServicesCollection      string
	DatasetsCollection          string
	Database                    string
	EventsCollection            string
	InformationModelsCollection string
	ServicesCollection          string
}

type KafkaConstants struct {
	RdfParseTopic string
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
	Services          string
	Service           string
}

type Security struct {
	TokenAudience string
	SysAdminAuth  string
}

var MongoValues = MongoConstants{
	ConceptsCollection:          "concepts",
	DataServicesCollection:      "dataServices",
	DatasetsCollection:          "datasets",
	Database:                    "fdkResourceService",
	EventsCollection:            "events",
	InformationModelsCollection: "informationModels",
	ServicesCollection:          "services",
}

var KafkaValues = KafkaConstants{
	RdfParseTopic: "rdf-parse-events",
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
	Services:          "/services",
	Service:           "/services/:id",
}

var SecurityValues = Security{
	TokenAudience: "fdk-harvest-admin",
	SysAdminAuth:  "system:root:admin",
}
