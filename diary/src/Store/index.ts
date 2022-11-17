export interface JournelData {
    weather: string,
    feeling: string,
    location: string | null,
    body: string
}

export interface status_t {
    currentDate: Date,
    promptStatusText: string,
    currentjournel: JournelData | null,
    edited: boolean
}
