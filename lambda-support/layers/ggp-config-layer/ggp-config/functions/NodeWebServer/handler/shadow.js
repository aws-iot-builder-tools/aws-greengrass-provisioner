// const greengrass = require('aws-greengrass-core-sdk');
// const iotData = new greengrass.IotData();
const { IoTDataPlaneClient, GetThingShadowCommand, UpdateThingShadowCommand, DeleteThingShadowCommand } = require("@aws-sdk/client-iot-data-plane");

// module.exports.checkShadow = async (params) => {

// 	console.log('checkShadow for: record.thingName:' + JSON.stringify(params));

// 	return new Promise((resolve, reject) => {
// 		iotData.getThingShadow(params, (err, data) => {
// 			if (err) {
// 				console.log("checkShadow: " + JSON.stringify(error));
// 				reject(err);
// 			} else {
// 				console.log('checkShadow out: result:' + data);
// 				resolve(JSON.parse(data));
// 			}
//  		});
// 	});	
// };

module.exports.getShadow = async (params) => {

	console.log('getShadow in: params:' + JSON.stringify(params));

	const client = new IoTDataPlaneClient({});

	const command = new GetThingShadowCommand(params);

	const objResult = await client.send(command);

	let result = {};
	if (objResult) {
		const returnArray = Object.values(objResult.payload);

		result = JSON.parse(String.fromCharCode.apply(null, new Uint8Array(returnArray)));
	}

	console.log('getShadow out: result:' + JSON.stringify(result));

	return result;

};

module.exports.updateReportedShadow = async (params) => {

	console.log('updateReportedShadow in: params:' + JSON.stringify(params));

	let newParams = {
		thingName: params.thingName
	}

	if (params.shadowName) {
		newParams.shadowName = params.shadowName;
	}

	if (params.reportedState) {
		newParams.payload = Buffer.from(JSON.stringify({
            "state": {
                "reported": params.reportedState
            }
		}));
	}

	return await updateShadow(newParams);
};

module.exports.deleteShadow = async (params) => {

	console.log('deleteShadow in: params:' + JSON.stringify(params));

	const client = new IoTDataPlaneClient({});

	const command = new DeleteThingShadowCommand(params);

	const objResult = await client.send(command);

	let result = {};
	if (objResult) {
		const returnArray = Object.values(objResult.payload);

		result = JSON.parse(String.fromCharCode.apply(null, new Uint8Array(returnArray)));
	}

	console.log('updateShadow out: result:' + JSON.stringify(result));

	return result;

};

const updateShadow = async (params) => {

	console.log('updateShadow in: params:' + JSON.stringify(params));

	const client = new IoTDataPlaneClient({});

	const command = new UpdateThingShadowCommand(params);

	const objResult = await client.send(command);

	let result = {};
	if (objResult) {
		const returnArray = Object.values(objResult.payload);

		result = JSON.parse(String.fromCharCode.apply(null, new Uint8Array(returnArray)));
	}

	console.log('updateShadow out: result:' + JSON.stringify(result));

	return result;
};