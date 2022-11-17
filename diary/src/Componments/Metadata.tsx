import { Form, FormField, Select, TextInput } from "grommet";
import { Component, ReactNode } from "react";
import { connect } from "react-redux";
import { status_t } from "../Store";

interface MetadataEditor_Props {
    currentFeeling: string,
    currentWeather: string,
    currentLocation: string | null
    updateFeeling: (n: string) => void,
    updateWeather: (n: string) => void
}

class MetadataEditor extends Component {
    constructor(props: any) {
        super(props);
    }

    render(): ReactNode {
        const mappedProps = this.props as MetadataEditor_Props;
        return (
            <div id='metadataArea'>
                <Form>
                    <FormField name="Weather" htmlFor='weather' label="Weather">
                        <Select
                            id='weather'
                            options={[
                                'cloudy',
                                'sunny',
                                'foggy',
                                'hurricane',
                                'windy',
                                'lighting',
                                'rainy',
                                'snowy',
                                'stormy'
                            ]}
                            value={mappedProps.currentWeather || "Loading..."}
                            onChange={e => mappedProps.updateWeather(e.value)}
                        />
                    </FormField>
                    <FormField label="Feelings">
                        <TextInput
                            placeholder="type here"
                            value={mappedProps.currentFeeling}
                            onChange={e => { mappedProps.updateFeeling(e.currentTarget.value || "Unknown") }}
                        />
                    </FormField>
                    <FormField label="Last Modify">
                        <TextInput
                            value={mappedProps.currentLocation || "Unknown"}
                            disabled
                        />
                    </FormField>
                </Form>
            </div>
        )
    }
}

function mapStateToProps(state: status_t) {
    return {
        currentFeeling: state.currentjournel?.feeling,
        currentWeather: state.currentjournel?.weather,
        currentLocation: state.currentjournel?.location
    }
}

function mapDispatchToProps(dispatch: any) {
    return {
        updateFeeling(nf: string) {
            dispatch({
                type: "updateFeeling",
                body: nf
            })
        },
        updateWeather(nw: string) {
            dispatch({
                type: "updateWeather",
                body: nw
            })
        }
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(MetadataEditor)