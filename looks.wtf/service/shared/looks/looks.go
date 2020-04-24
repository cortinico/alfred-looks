package looks

import (
	"io"
	"log"
	"strings"

	yaml "gopkg.in/yaml.v2"
)

type Look struct {
	Plain string `yaml:"plain"`
	Tags  string `yaml:"tags"`
}

func ParseLooks(r io.Reader) map[string][]Look {
	var looks []Look
	dec := yaml.NewDecoder(r)
	err := dec.Decode(&looks)
	if err != nil {
		log.Fatal("Error unmarshaling looks yaml:", err)
	}

	var looksByTags = make(map[string][]Look)
	for _, l := range looks {
		tags := strings.Split(l.Tags, " ")
		for _, t := range tags {
			if t == "" {
				continue
			}
			looksByTag, ok := looksByTags[t]
			if !ok {
				looksByTag = []Look{}
			}
			looksByTags[t] = append(looksByTag, l)
		}
	}
	return looksByTags
}
