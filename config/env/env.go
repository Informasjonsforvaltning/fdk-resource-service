package env

import "os"

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}

func MongoHost() string {
	return getEnv("MONGO_HOST", "localhost:27017")
}

func MongoPassword() string {
	return getEnv("MONGO_PASSWORD", "admin")
}

func MongoUsername() string {
	return getEnv("MONGO_USERNAME", "admin")
}

func MongoAuthSource() string {
	return getEnv("MONGODB_AUTH", "admin")
}

func MongoReplicaSet() string {
	return getEnv("MONGODB_REPLICASET", "")
}

type MongoConstants struct {
	DatasetsCollection string
	Database           string
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

var PathValues = Paths{
	Ping:  "/ping",
	Ready: "/ready",
	Datasets:  "/datasets",
	Dataset: "/datasets/:id",
}
