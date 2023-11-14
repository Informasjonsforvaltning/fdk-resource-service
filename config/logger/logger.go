package logger

import (
	"context"
	"os"

	"cloud.google.com/go/errorreporting"
	"github.com/sirupsen/logrus"
)

func ConfigureLogger() {
	formatter := logrus.JSONFormatter{
		FieldMap: logrus.FieldMap{
			logrus.FieldKeyLevel: "severity",
		},
	}
	logrus.SetFormatter(&formatter)

	errorClientSetup()
}

var errorClient *errorreporting.Client

func errorClientSetup() {
	// GCP errorreporting should only be configured when running in GCP,
	// setup of errorClient is therefore skipped when missing GCP env values
	if projectId, ok := os.LookupEnv("PROJECT_ID_GCP"); ok {
		var err error
		errorClient, err = errorreporting.NewClient(context.Background(), projectId, errorreporting.Config{
			ServiceName: "fdk-resource-service",
			OnError: func(err error) {
				logrus.Errorf("Could not log error: %v", err)
			},
		})
		if err != nil {
			logrus.Error(err)
		}
	}
}

func LogAndPrintError(err error) {
	if errorClient != nil {
		errorClient.Report(errorreporting.Entry{
			Error: err,
		})
	}
	logrus.Error(err)
}
