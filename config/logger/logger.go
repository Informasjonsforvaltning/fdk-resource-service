package logger

import "github.com/sirupsen/logrus"

func ConfigureLogger() {
	formatter := logrus.JSONFormatter{
		FieldMap: logrus.FieldMap{
			logrus.FieldKeyLevel: "severity",
		},
	}
	logrus.SetFormatter(&formatter)
}
