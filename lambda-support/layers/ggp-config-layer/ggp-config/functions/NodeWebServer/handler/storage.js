const AWS = require('aws-sdk');

const config = {
  endpoint: process.env.DDB_ENDPOINT || 'http://192.168.11.36:8080',
  region: 'ap-northeast-1',
  accessKeyId: '',
  secretAccessKey: ''
};

AWS.config.update(config);
const docClient = new AWS.DynamoDB.DocumentClient();

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


