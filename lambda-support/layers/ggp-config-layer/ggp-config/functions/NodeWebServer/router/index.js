const deviceReg = require('./deviceReg');
const scanResult = require('./scanResult');

module.exports = app => {
  app.use('/aiFace/dev2service/api', deviceReg);
  app.use('/aiFace/dev2service/api', scanResult);
}