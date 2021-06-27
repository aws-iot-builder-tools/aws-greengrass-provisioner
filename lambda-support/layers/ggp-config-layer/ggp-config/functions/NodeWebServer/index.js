const express = require('express');
const mountRoutes = require('./router');

const storage = require('./handler/storage');
const shadow = require('./handler/shadow');
const eventHandler = require('./handler/event');

const GROUP_ID = process.env.GROUP_ID
const AWS_IOT_THING_NAME = process.env.AWS_IOT_THING_NAME;
const AWS_IOT_THING_ARN = process.env.AWS_IOT_THING_ARN;
const AWS_GREENGRASS_GROUP_NAME = process.env.AWS_GREENGRASS_GROUP_NAME;
const PORT = process.env.PORT || 8081;

const base_topic = AWS_IOT_THING_NAME + '/web_server_node'
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
    // console.log('AWS_IOT_THING_NAME: ' + AWS_IOT_THING_NAME);
    console.log('base_topic: ' + base_topic);

    try {
        if (context.clientContext.Custom.subject.indexOf('reservation_update') > -1) {

            await storage.saveReservationRecord(event);

        } else if (context.clientContext.Custom.subject.indexOf('member_update') > -1) {

            await storage.saveMemberData(event);
        } else if (context.clientContext.Custom.subject.indexOf('list_tables') > -1) {
            const tableList = await storage.listTables();

            console.log('tableList: ' + JSON.stringify(tableList));
        } else if (context.clientContext.Custom.subject.indexOf('get_reservation') > -1) {
            const result = await storage.getReservation({
                listingId: event.listingId,
                reservationCode: event.reservationCode
            });

            console.log('result: ' + JSON.stringify(result));

        } else if (context.clientContext.Custom.subject.indexOf('check_shadow') > -1) {
            console.log('event.shadowName:: ' + event.shadowName);

            const getShadowResult = await shadow.getShadow({
                thingName: AWS_IOT_THING_NAME,
                shadowName: event.shadowName
            });

            console.log('getShadowResult caller: ' + JSON.stringify(getShadowResult));

            await storage.saveReservation({
                reservation: getShadowResult.state.desired.reservation,
                members: getShadowResult.state.desired.members,
                version: getShadowResult.version
            });


        } else if (context.clientContext.Custom.subject.indexOf('sync_reservation') > -1) {
            console.log('event.shadowName:: ' + event.shadowName);

            await eventHandler.syncReservation({
                shadowName: event.shadowName
            });

        } else if (context.clientContext.Custom.subject.indexOf('delete_shadow') > -1) {
            console.log('event.shadowName:: ' + event.shadowName);

            const deleteShadowResult = await shadow.deleteShadow({
                thingName: AWS_IOT_THING_NAME,
                shadowName: event.shadowName
            });

            console.error('deleteShadowResult: ' + JSON.stringify(deleteShadowResult));

        }
    } catch (err) {
        console.error('!!!!!!error happened at handler error start!!!!!!');
        console.error(err.name);
        console.error(err.message);
        console.error(err.stack);
        console.trace();
        console.error('!!!!!!error happened at handler error end!!!!!!');
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


// shadow.getShadow({
//     thingName: AWS_IOT_THING_NAME
// }).then(value => {
//     console.log('getShadow startupShadowResult');
// });


