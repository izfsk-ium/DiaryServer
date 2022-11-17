import React from 'react'
import ReactDOM from 'react-dom/client'
import './App.css'
import { Grommet } from 'grommet'
import 'react-calendar/dist/Calendar.css';
import 'react-quill/dist/quill.snow.css';
import CalendarChooser from './Componments/Calender';
import MetadataEditor from './Componments/Metadata';
import MainEditor from './Componments/Editor';
import JPageHeader from './Componments/PageHeader';
import { Provider } from 'react-redux';
import { store } from './main';

export function initApplication() {
    function App() {
        return (
            <Provider store={store as any}>
                <Grommet>
                    <JPageHeader />
                    <div className='mainGrid'>
                        <CalendarChooser></CalendarChooser>
                        <MetadataEditor></MetadataEditor>
                        <MainEditor></MainEditor>
                    </div>
                </Grommet >
            </Provider>
        )
    }

    ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
        <App />
    )
}