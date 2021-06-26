const storage = require('../handler/storage');
const Router = require('express-promise-router');

const router = new Router();

// export our router to be mounted by the parent application
module.exports = router

router.post('/uploadMipsGateRecord', async (req, res) => {

  const payload = Object.assign({}, req.body);

  delete payload.checkPic;

  console.log('uploadMipsGateRecord payload:' + JSON.stringify(payload));

  // await storage.saveScanRecord(payload);

  const response = {
      "code":0,
      "message":"Good!" 
  };

  res.send(response);
  
})