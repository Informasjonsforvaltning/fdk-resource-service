package validate

import "regexp"

func SanitizeID(id string) string {
	reg, _ := regexp.Compile("[^-0-9a-zA-Z]")
	return reg.ReplaceAllString(id, "")
}
