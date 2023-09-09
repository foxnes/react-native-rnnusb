# react-native-rnnusb

USB communication library for react-native (Android only). You can specify the PID and VID of the USB device, listen for permissions, USB device connection, USB data and send data in Javascript.

## Getting started

`$ npm install git@github.com:foxnes/react-native-rnnusb.git --save`

## Usage

First of all, import the lib and specify the PID and VID of your device.
```js
import RNNUSB from "react-native-rnnusb";
const VID = 0x99AF;
const PID = 0x2B1F;
RNNUSB.setTarget(VID, PID);
```

Listen for USB device connection and disconnection.
```js
const unsub = RNNUSB.subscribeUSBConnection((info) => {
    console.log(TAG, "USB Connection: " + info);
    if (info === RNNUSB.CONSTANTS.MSG_USB_CONNECTION_ATTACHED) {
        console.log("USB device connected");
    } else if (info === RNNUSB.CONSTANTS.MSG_USB_CONNECTION_DETACHED) {
        console.log("USB device disconnected");
    }
});
// unsubscribe it:
// unsub();
```

Listen for data.
```js
const unsub = RNNUSB.subscribeUSBData(data => {
  console.log("Recv USB data: " + data);
});
// unsubscribe it:
// unsub();
```

Listen for USB permissions (granting and denying).
```js
const unsub = RNNUSB.subscribeUSBInform((info) => {
    // info can be PERMISSION_DENIED | MSG_PERMISSION_GRANTED | other errors.
    console.log(info);
    if (value === RNNUSB.CONSTANTS.MSG_PERMISSION_DENIED) {
    	console.log("PERMISSION DENIED!");
    } else if (value === RNNUSB.CONSTANTS.MSG_PERMISSION_GRANTED) {
    	console.log("PERMISSION GRANTED!");
    }
});
// unsubscribe it:
// unsub();
```

Send data.
```js
const data = [0xaa, 0xbb, 0x12];
RNNUSB.write(Buffer.from(data).toString('hex'));
RNNUSB.write("5fe80a");

(async () => {
    await RNNUSB.write(Buffer.from(data).toString('hex'));
    await RNNUSB.write("aabb12");
})();
```
`RNNUSB.write()` requires a hex string as its only parameter (Can not contain spaces / prefix "0x" / commas) and will return a `Promise`. If no USB connection established, the `Promise` will be rejected, otherwise it will be resolved.