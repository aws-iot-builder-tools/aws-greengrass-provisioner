const express = require('express');
const mountRoutes = require('./router');

// const ggSdk = require('aws-greengrass-core-sdk');
// const iotClient = new ggSdk.IotData();
const storage = require('./handler/storage');

const GROUP_ID = process.env.GROUP_ID
const THING_NAME = process.env.AWS_IOT_THING_NAME;
const THING_ARN = process.env.AWS_IOT_THING_ARN;
const PORT = process.env.PORT || 8081;

const base_topic = THING_NAME + '/web_server_node'
const log_topic = base_topic + '/log'

function publishCallback(err, data) {
    console.log('publishCallback');
    console.log(err);
    console.log(data);
}

// This is a handler which does nothing for this example
exports.handler = async function(event, context) {
    console.log('event: ' + JSON.stringify(event));
    console.log('context: ' + JSON.stringify(context));

    if (context.clientContext.Custom.subject.indexOf('reservation_update') > -1) {

        await storage.saveReservationData(event);

    } else if (context.clientContext.Custom.subject.indexOf('member_update') > -1) {

        await storage.saveMemberData(event);
    }  else if (context.clientContext.Custom.subject.indexOf('list_tables') > -1) {
        const tableList = await storage.listTables();

        console.log('tableList: ' + JSON.stringify(tableList));
    }

    const promise = new Promise(function(resolve, reject) {
      console.log("Promise callback");
      resolve();
    });

    return promise;
};



const app = express();

app.use(express.json()); 
app.use(express.urlencoded({extended: true}));
mountRoutes(app);

/*
app.get('/', (req, res) => {
        res.send('Hello World!')

        const pubOpt = {
           topic: log_topic,
           payload: JSON.stringify({ message: 'Hello World request serviced' })
        };

        iotClient.publish(pubOpt, publishCallback);
    });

app.post('/aiFace/dev2service/api/deviceReg', (req, res) => {
        console.log('req.body', req.body);
        const response = {
            "code":0,
            "message":"Good!" 
        };
        res.send(response);

        const pubOpt = {
           topic: log_topic,
           payload: JSON.stringify(req.body)
        };

        iotClient.publish(pubOpt, publishCallback);        
    });

app.post('/aiFace/dev2service/api/uploadMipsGateRecord', (req, res) => {
        // console.log('req.body', req.body);
        const response = {
            "code":0,
            "message":"Good!" 
        };
        res.send(response);

        const payload = Object.assign({}, req.body);

        delete payload.checkPic;

        const pubOpt = {
           topic: log_topic,
           payload: JSON.stringify(payload)
        };

        iotClient.publish(pubOpt, publishCallback);

        storage.saveScanRecord(payload);
    });
*/

app.listen(PORT, () => console.log(`Example app listening on port ${PORT}!`));
