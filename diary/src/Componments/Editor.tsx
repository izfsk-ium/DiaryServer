import { Component, ReactNode } from "react";
import ReactQuill from "react-quill";
import { connect } from "react-redux";
import { status_t } from "../Store";

interface MainEditor_Props {
    fullTextDisplayed: string,
    editable: boolean,
    dateOfText: Date
    updateText: (nt: string, targetDate: Date) => any
}

class MainEditor extends Component {
    constructor(props: any) {
        super(props);
    }

    render(): ReactNode {
        const mappedProps = this.props as MainEditor_Props;
        console.log("Editor display text : " + mappedProps.fullTextDisplayed + " for " + mappedProps.dateOfText);
        return (
            <div id='editor'>
                <ReactQuill
                    theme="snow"
                    modules={{
                        clipboard: {
                            matchVisual: false
                        },
                        toolbar: [
                            [{ 'header': [1, 2, 3, 4, 5, false] },
                            { 'color': ['#abc123', 'red', 'black', 'brown', 'blue', 'gray', 'green', 'white', '#FF6F00', '#00C853', '#D81B60'] },
                            { 'background': ['#abc123', 'red', 'black', 'brown', 'blue', 'gray', 'green', 'white', '#FF6F00', '#00C853', '#D81B60'] }],
                            ['bold', 'italic', 'underline', 'align', 'strike', 'code-block',],
                            [{ 'list': 'ordered' }, { 'list': 'bullet' }, { 'indent': '-1' }, { 'indent': '+1' }],
                            ['link', 'image', 'formula'],
                            ['clean']
                        ],

                    }}
                    formats={[
                        'header', 'color', 'background',
                        'bold', 'italic', 'underline', 'strike', 'blockquote', 'code', 'font', 'script', 'code-block',
                        'list', 'bullet', 'indent',
                        'link', 'image'
                    ]}
                    value={mappedProps.fullTextDisplayed}
                    onChange={e => { mappedProps.updateText(e, mappedProps.dateOfText) }}
                    readOnly={!mappedProps.editable}
                    placeholder={typeof mappedProps.fullTextDisplayed === "undefined" || mappedProps.fullTextDisplayed.length == 0 ? "Nothing here~~" : ""}
                />
            </div>
        )
    }
}

function mapStateToProps(state: status_t) {
    return {
        fullTextDisplayed: state.currentjournel?.body,
        dateOfText: state.currentDate,
        editable: (state.currentDate.toDateString() === new Date().toDateString())
    }
}

function mapDispatchToProps(dispatch: any) {
    return {
        updateText(nt: string, targetDate: Date) {
            dispatch({
                type: "updateText",
                body: {
                    targetDate: targetDate,
                    text: nt
                }
            })
        },
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(MainEditor)