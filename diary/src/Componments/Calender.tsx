import { Component, ReactNode } from "react";
import { Calendar } from "react-calendar";
import { connect } from "react-redux";
import { status_t } from "../Store";

import swal from "sweetalert2";
import * as openpgp from 'openpgp';
import { keyStore } from "../main";

export interface CalendarChooser_Props {
    currentDate: Date,
    switchDate: (d: Date) => any,
}

class CalendarChooser extends Component {
    constructor(props: any) {
        super(props);
    }

    render(): ReactNode {
        const mappedProps: CalendarChooser_Props = this.props as CalendarChooser_Props;
        return (
            <div id='calendarArea'>
                <div className='calendar-container'>
                    <Calendar
                        value={mappedProps.currentDate}
                        onChange={async (value: Date, event: any) => {
                            const now = new Date();
                            if (value > now) {
                                await swal.mixin({
                                    toast: true,
                                    position: 'top-end',
                                    showConfirmButton: false,
                                    timer: 2500
                                }).fire({
                                    icon: 'error',
                                    title: 'You cannot edit the future!'
                                })
                                return;
                            } else {
                                await mappedProps.switchDate(value);
                            }
                        }} />
                </div>
            </div>
        )
    }
}

function mapStateToProps(state: status_t) {
    return {
        currentDate: state.currentDate
    }
}

function mapDispatchToProps(dispatch: any) {
    return {
        async switchDate(newDate: Date) {
            if (!keyStore.isAuthFinished) {
                return;
            }
            console.log("switching date to " + newDate.toDateString());
            // fetch data
            const requestResult =
                await fetch("/data/" + newDate.toLocaleDateString("zh-CN"), {
                    method: "GET",
                    headers: {
                        "x-token": keyStore.serverSideSalt + keyStore.clientSideSalt
                    }
                })
                    .then(resp => { return resp.text() })
                    .catch(e => { return null });

            // check if fetch succeed
            if (requestResult == null) {
                swal.fire({
                    icon: "error",
                    title: "Error!",
                    text: "Failed to load data!"
                })
                return;
            }

            // check if this day has no data ("null")
            if (requestResult === "null") {
                dispatch({
                    type: "updateDate",
                    body: { date: newDate, journel: null }
                })
                swal.mixin({
                    toast: true,
                    position: 'top-end',
                    showConfirmButton: false,
                    timer: 3000
                }).fire({
                    icon: 'info',
                    title: 'This day have no data yet!'
                })
                return;
            }

            // decrypt pgp data and verify signature
            const pgpCipherText = await openpgp.readMessage({ armoredMessage: requestResult || "" });
            const [decryptedJSONText, detachedSignatures] = await openpgp.decrypt({
                message: pgpCipherText,
                verificationKeys: keyStore.publicKey,
                decryptionKeys: keyStore.privateKey
            })
                .then(result => { return [result.data, result.signatures] })
                .catch(e => { return [null, null] });
            if (decryptedJSONText == null || detachedSignatures == null) {
                swal.fire({
                    icon: "error",
                    title: "Error!",
                    text: "Failed to load data!"
                })
            }

            console.warn("Journel data after fetch before dispatch:", JSON.parse(decryptedJSONText as string).body);
            // update Data
            await dispatch({
                type: "updateDate",
                body: { date: newDate, journel: JSON.parse(decryptedJSONText as string) }
            })
        }
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(CalendarChooser)