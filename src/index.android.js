import { NativeModules, processColor } from "react-native";

const RNCalendarEvents = NativeModules.RNCalendarEvents;

export default {
  async checkPermissions(readOnly = false) {
    return RNCalendarEvents.checkPermissions(readOnly);
  },
  async requestPermissions(readOnly = false) {
    return RNCalendarEvents.requestPermissions(readOnly);
  },

  async fetchAllEvents(startDate, endDate, calendars = []) {
    return RNCalendarEvents.findAllEvents(startDate, endDate, calendars);
  },

  async findCalendars() {
    return RNCalendarEvents.findCalendars();
  },

  async saveCalendar(options = {}) {
    const cid = await RNCalendarEvents.saveCalendar({
      ...options,
      color: options.color ? processColor(options.color) : undefined,
    });
    if(!cid) throw 'saveCalendar error';

    const calendars = await RNCalendarEvents.findCalendars();
    if(!calendars || !calendars.length || !calendars.some(c=>c.id==cid)) {
      throw 'saveCalendar error';
    }

    return cid;
  },

  async removeCalendar(id) {
    return RNCalendarEvents.removeCalendar(id);
  },

  async findEventById(id) {
    return RNCalendarEvents.findById(id);
  },

  async saveEvent(title, details, options = { sync: false }) {
    return RNCalendarEvents.saveEvent(title, details, options);
  },

  async removeEvent(id, options = { sync: false }) {
    return RNCalendarEvents.removeEvent(id, options);
  },
  
  async saveEvents (detailsList, options = {sync: false}) {
    return RNCalendarEvents.saveEvents(detailsList, options)
  },
  
  async removeEvents (ids, options = {sync: false}) {
    return RNCalendarEvents.removeEvents(ids, options)
  },

  async uriForCalendar() {
    return RNCalendarEvents.uriForCalendar();
  },

  openEventInCalendar(eventID) {
    RNCalendarEvents.openEventInCalendar(eventID);
  },
};
