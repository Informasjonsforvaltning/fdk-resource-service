// Code generated by github.com/actgardner/gogen-avro/v10. DO NOT EDIT.
/*
 * SOURCE:
 *     service_schema.avsc
 */
package avro

import (
	"encoding/json"
	"fmt"
	"io"

	"github.com/actgardner/gogen-avro/v10/compiler"
	"github.com/actgardner/gogen-avro/v10/vm"
	"github.com/actgardner/gogen-avro/v10/vm/types"
)

var _ = fmt.Printf

type ServiceEvent struct {
	Type ServiceEventType `json:"type"`

	FdkId string `json:"fdkId"`

	Graph string `json:"graph"`

	Timestamp int64 `json:"timestamp"`
}

const ServiceEventAvroCRC64Fingerprint = "'\x0f}a\x1e\xe8N\xd6"

func NewServiceEvent() ServiceEvent {
	r := ServiceEvent{}
	return r
}

func DeserializeServiceEvent(r io.Reader) (ServiceEvent, error) {
	t := NewServiceEvent()
	deser, err := compiler.CompileSchemaBytes([]byte(t.Schema()), []byte(t.Schema()))
	if err != nil {
		return t, err
	}

	err = vm.Eval(r, deser, &t)
	return t, err
}

func DeserializeServiceEventFromSchema(r io.Reader, schema string) (ServiceEvent, error) {
	t := NewServiceEvent()

	deser, err := compiler.CompileSchemaBytes([]byte(schema), []byte(t.Schema()))
	if err != nil {
		return t, err
	}

	err = vm.Eval(r, deser, &t)
	return t, err
}

func writeServiceEvent(r ServiceEvent, w io.Writer) error {
	var err error
	err = writeServiceEventType(r.Type, w)
	if err != nil {
		return err
	}
	err = vm.WriteString(r.FdkId, w)
	if err != nil {
		return err
	}
	err = vm.WriteString(r.Graph, w)
	if err != nil {
		return err
	}
	err = vm.WriteLong(r.Timestamp, w)
	if err != nil {
		return err
	}
	return err
}

func (r ServiceEvent) Serialize(w io.Writer) error {
	return writeServiceEvent(r, w)
}

func (r ServiceEvent) Schema() string {
	return "{\"fields\":[{\"name\":\"type\",\"type\":{\"name\":\"ServiceEventType\",\"symbols\":[\"SERVICE_HARVESTED\",\"SERVICE_REASONED\",\"SERVICE_REMOVED\"],\"type\":\"enum\"}},{\"name\":\"fdkId\",\"type\":\"string\"},{\"name\":\"graph\",\"type\":\"string\"},{\"logicalType\":\"timestamp-millis\",\"name\":\"timestamp\",\"type\":\"long\"}],\"name\":\"no.fdk.service.ServiceEvent\",\"type\":\"record\"}"
}

func (r ServiceEvent) SchemaName() string {
	return "no.fdk.service.ServiceEvent"
}

func (_ ServiceEvent) SetBoolean(v bool)    { panic("Unsupported operation") }
func (_ ServiceEvent) SetInt(v int32)       { panic("Unsupported operation") }
func (_ ServiceEvent) SetLong(v int64)      { panic("Unsupported operation") }
func (_ ServiceEvent) SetFloat(v float32)   { panic("Unsupported operation") }
func (_ ServiceEvent) SetDouble(v float64)  { panic("Unsupported operation") }
func (_ ServiceEvent) SetBytes(v []byte)    { panic("Unsupported operation") }
func (_ ServiceEvent) SetString(v string)   { panic("Unsupported operation") }
func (_ ServiceEvent) SetUnionElem(v int64) { panic("Unsupported operation") }

func (r *ServiceEvent) Get(i int) types.Field {
	switch i {
	case 0:
		w := ServiceEventTypeWrapper{Target: &r.Type}

		return w

	case 1:
		w := types.String{Target: &r.FdkId}

		return w

	case 2:
		w := types.String{Target: &r.Graph}

		return w

	case 3:
		w := types.Long{Target: &r.Timestamp}

		return w

	}
	panic("Unknown field index")
}

func (r *ServiceEvent) SetDefault(i int) {
	switch i {
	}
	panic("Unknown field index")
}

func (r *ServiceEvent) NullField(i int) {
	switch i {
	}
	panic("Not a nullable field index")
}

func (_ ServiceEvent) AppendMap(key string) types.Field { panic("Unsupported operation") }
func (_ ServiceEvent) AppendArray() types.Field         { panic("Unsupported operation") }
func (_ ServiceEvent) HintSize(int)                     { panic("Unsupported operation") }
func (_ ServiceEvent) Finalize()                        {}

func (_ ServiceEvent) AvroCRC64Fingerprint() []byte {
	return []byte(ServiceEventAvroCRC64Fingerprint)
}

func (r ServiceEvent) MarshalJSON() ([]byte, error) {
	var err error
	output := make(map[string]json.RawMessage)
	output["type"], err = json.Marshal(r.Type)
	if err != nil {
		return nil, err
	}
	output["fdkId"], err = json.Marshal(r.FdkId)
	if err != nil {
		return nil, err
	}
	output["graph"], err = json.Marshal(r.Graph)
	if err != nil {
		return nil, err
	}
	output["timestamp"], err = json.Marshal(r.Timestamp)
	if err != nil {
		return nil, err
	}
	return json.Marshal(output)
}

func (r *ServiceEvent) UnmarshalJSON(data []byte) error {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(data, &fields); err != nil {
		return err
	}

	var val json.RawMessage
	val = func() json.RawMessage {
		if v, ok := fields["type"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.Type); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for type")
	}
	val = func() json.RawMessage {
		if v, ok := fields["fdkId"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.FdkId); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for fdkId")
	}
	val = func() json.RawMessage {
		if v, ok := fields["graph"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.Graph); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for graph")
	}
	val = func() json.RawMessage {
		if v, ok := fields["timestamp"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.Timestamp); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for timestamp")
	}
	return nil
}
