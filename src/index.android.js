import { NativeModules, processColor } from "react-native";

const RNCalendarEvents = NativeModules.RNCalendarEvents;

export default {
  async checkPermissions(readOnly = false) {
    return RNCalendarEvents.checkPermissions(readOnly);
  },
  async requestPermissions(readOnly = false) {
    return RNCalendarEvents.requestPermissions(readOnly);
  },

  async saveCalendar(options = {}) {
    return await RNCalendarEvents.saveCalendar({
      ...options,
      color: options.color ? processColor(options.color) : undefined,
    });

  },

  async removeCalendar(id) {
    return RNCalendarEvents.removeCalendar(id);
  },

  async removeCalendarByName(name) {
    return RNCalendarEvents.removeCalendarByName(name);
  },

  async findCalendarId(name) {
    return RNCalendarEvents.findCalendarId(name);
  },

  async saveEvents(detailsList) {
    return RNCalendarEvents.saveEvents(detailsList)
  },
  
  async saveEvent(detail) {
    return RNCalendarEvents.saveEvent(detail)
  },

  async removeEvents(detail) {
    return RNCalendarEvents.removeEvents(detail)
  },
  async updateEvent(detail) {
    return RNCalendarEvents.updateEvent(detail)
  },

  async uriForCalendar() {
    return RNCalendarEvents.uriForCalendar();
  },

  openEventInCalendar(eventID) {
    RNCalendarEvents.openEventInCalendar(eventID);
  },
};
