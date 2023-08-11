# react-native-rnnusb

## Getting started

`$ npm install git@github.com:foxnes/react-native-rnnusb.git --save`

## Usage
```javascript
import RNNUSB from "react-native-rnnusb";
const VID = 0x99AF;
const PID = 0x2B1F;
RNNUSB.setTarget(VID, PID);

RNNUSB.subscribeUSBConnection((info) => {
    console.log(TAG, "USB Connection: " + info);
    if (info === RNNUSB.CONSTANTS.MSG_USB_CONNECTION_ATTACHED) {
        console.log("Connected to RC");
    } else if (info === RNNUSB.CONSTANTS.MSG_USB_CONNECTION_DETACHED) {
        console.log("Disconnected from RC");
    }
});

RNNUSB.subscribeUSBData(data => {
  console.log("Recv USB data: " + data);
});

RNNUSB.subscribeUSBInform(info => {
    // info can be PERMISSION_DENIED | MSG_PERMISSION_GRANTED | other errors.
    console.log(info);
    if (value === RNNUSB.CONSTANTS.MSG_PERMISSION_DENIED) {
    	console.log("PERMISSION DENIED!");
    } else if (value === RNNUSB.CONSTANTS.MSG_PERMISSION_GRANTED) {
    	console.log("PERMISSION GRANTED!");
    }
});

const data = [0xaa, 0xbb, 0x12];
RNNUSB.write(Buffer.from(data).toString('hex'));
RNNUSB.write("aabb12");
```
