package model

type DBO struct {
	ID        string                 `bson:"_id"`
	Resource  map[string]interface{} `bson:"resource"`
	Timestamp int64                  `bson:"timestamp"`
	Deleted   bool                   `bson:"deleted"`
}
