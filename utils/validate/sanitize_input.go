package validate

import (
	"errors"
	"regexp"
	"strings"
)

func SanitizeID(id string) string {
	// Remove any characters that are not alphanumeric or hyphens
	reg, _ := regexp.Compile("[^-0-9a-zA-Z]")
	sanitized := reg.ReplaceAllString(id, "")
	
	// Remove leading/trailing hyphens and multiple consecutive hyphens
	reg2, _ := regexp.Compile("^-+|-+$")
	sanitized = reg2.ReplaceAllString(sanitized, "")
	
	reg3, _ := regexp.Compile("-+")
	sanitized = reg3.ReplaceAllString(sanitized, "-")
	
	return strings.TrimSpace(sanitized)
}


// SanitizeAndValidateID combines sanitation and validation, returning error if not valid
func SanitizeAndValidateID(id string) (string, error) {
    sanitized := SanitizeID(id)
    if err := ValidateID(sanitized); err != nil {
        return "", err
    }
    return sanitized, nil
}

// ValidateID performs additional validation on sanitized IDs
func ValidateID(id string) error {
	if id == "" {
		return errors.New("ID cannot be empty")
	}
	// Disallow IDs with only hyphens
	if matched, _ := regexp.MatchString("^[-]+$", id); matched {
		return errors.New("ID cannot consist solely of hyphens")
	}
	// Require at least one alphanumeric character
	if matched, _ := regexp.MatchString("^[^-0-9a-zA-Z]*$", id); matched {
		return errors.New("ID must contain at least one alphanumeric character")
	}
	if len(id) > 100 {
		return errors.New("ID too long")
	}
	
	// Check for dangerous patterns
	if strings.Contains(id, "..") || strings.Contains(id, "//") {
		return errors.New("ID contains dangerous patterns")
	}
	
	return nil
}
