import { Anchor, Button, Clock, PageHeader, Spinner } from "grommet";
import { Component, ReactNode } from "react";
import { connect } from "react-redux";
import { status_t } from "../Store";

interface JPageHeader_Props {
    currentDate: Date
    promptStatusText: string,
    manuallySave: () => any
}

class JPageHeader extends Component<JPageHeader_Props, {}> {
    constructor(props: any) {
        super(props);
    }

    render(): ReactNode {
        const mappedProps = this.props as JPageHeader_Props;
        return (
            <PageHeader
                id="_header"
                title={<Clock type="digital" size='xxlarge'></Clock>}
                subtitle={mappedProps.promptStatusText}
                parent={<Anchor label={'Today: ' + new Date().toLocaleDateString('zh-CN')} /> || <Spinner />}
                actions={<div><Button label="Save" primary
                    onClick={e => { mappedProps.manuallySave() }}
                /></div>}
            />
        )
    }
}

function mapStateToProps(state: status_t) {
    return {
        currentDate: state.currentDate,
        promptStatusText: state.promptStatusText
    }
}

function mapDispatchToProps(dispatch: any) {
    return {
        manuallySave() {
            dispatch({
                type: "manuallySave",
                body: null
            })
        }
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(JPageHeader)