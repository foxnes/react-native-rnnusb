import {
  NativeModules,
  DeviceEventEmitter
  // @ts-ignore
} from 'react-native';

// native interface
interface NativeUsb {
  setTarget: (VID: number, PID: number) => void;
  write: (data: string) => Promise<void>;
}

const usb: NativeUsb = NativeModules.Rnnusb;

enum CONSTANTS {
  EVENT_USB_RECV_DATA = "USB_RECV_DATA",
  EVENT_USB_CONNECTION = "USB_CONNECTION",
  MSG_USB_CONNECTION_ATTACHED = "Attached",
  MSG_USB_CONNECTION_DETACHED = "Detached",
  EVENT_USB_INFORM = "USB_INFORM",
  MSG_PERMISSION_GRANTED = "PERMISSION_GRANTED",
  MSG_PERMISSION_DENIED = "PERMISSION_DENIED",
}

const RNNUSB = {
  CONSTANTS: CONSTANTS,

  setTarget(VID: number, PID: number) {
    usb.setTarget(VID, PID);
  },

  async write(data: string) {
    return usb.write(data);
  },

  subscribeUSBConnection(
    fcn: (
      info: CONSTANTS.MSG_USB_CONNECTION_ATTACHED | CONSTANTS.MSG_USB_CONNECTION_DETACHED
    ) => void) {
    return DeviceEventEmitter.addListener(CONSTANTS.EVENT_USB_CONNECTION, fcn);
  },

  subscribeUSBData(fcn: (data: string) => void) {
    return DeviceEventEmitter.addListener(CONSTANTS.EVENT_USB_RECV_DATA, fcn);
  },

  subscribeUSBInform(fcn: (
    info: CONSTANTS.MSG_PERMISSION_GRANTED | CONSTANTS.MSG_PERMISSION_DENIED | string
  ) => void) {
    return DeviceEventEmitter.addListener(CONSTANTS.EVENT_USB_INFORM, fcn);
  },

}

export default RNNUSB;