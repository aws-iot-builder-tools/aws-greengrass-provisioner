const deviceReg = require('./deviceReg');
const scanResult = require('./scanResult');
const temp = require('./temp');

module.exports = app => {
  app.use('/aiFace/dev2service/api', deviceReg);
  app.use('/aiFace/dev2service/api', scanResult);
  app.use('/temp', temp);
}