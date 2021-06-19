const AWS = require('aws-sdk');

const config = {
  endpoint: process.env.DDB_ENDPOINT || 'http://localhost:8080',
  region: 'ap-northeast-1',
  accessKeyId: '',
  secretAccessKey: ''
};

AWS.config.update(config);
const docClient = new AWS.DynamoDB.DocumentClient();
const ddb = new AWS.DynamoDB();

module.exports.saveScanRecord = async (record) => {

  console.log('saveScanRecord in: record:', record);

  const params = [{
    Put: {
      TableName: "ScanRecordTable",
      Item: record,
      ExpressionAttributeNames : {
          '#pk' : 'terminalMac'
      },
      ConditionExpression: 'attribute_not_exists(#pk)'
    }
  }];

  return await docClient.transactWrite({TransactItems: params}).promise().catch(error => {
      throw error;
    });
};

module.exports.saveReservationData = async (record) => {

  console.log('saveReservationData in: record:', record);

  const params = [{
    Put: {
      TableName: "GoCheckInReservation",
      Item: record,
      ExpressionAttributeNames : {
          '#pk' : 'listingId'
      },
      ConditionExpression: 'attribute_not_exists(#pk)'
    }
  }];

  return await docClient.transactWrite({TransactItems: params}).promise().catch(error => {
      throw error;
    });
};

module.exports.saveMemberData = async (record) => {

  console.log('saveMemberData in: record:', record);

  const params = [{
    Put: {
      TableName: "GoCheckInMember",
      Item: record,
      ExpressionAttributeNames : {
          '#pk' : 'reservationCode'
      },
      ConditionExpression: 'attribute_not_exists(#pk)'
    }
  }];

  return await docClient.transactWrite({TransactItems: params}).promise().catch(error => {
      throw error;
    });
};

module.exports.listTables = async () => {

  console.log('listTables in:');

  const params = {};

  return await ddb.listTables(params).promise().catch(error => {
      throw error;
  });
};