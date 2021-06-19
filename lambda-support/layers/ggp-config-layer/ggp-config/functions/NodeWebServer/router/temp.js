const Router = require('express-promise-router');

const router = new Router();

const storage = require('../handler/storage');

// export our router to be mounted by the parent application
module.exports = router

router.get('/listTable', async (req, res) => {

  

  const rtn = await storage.listTables();
  console.log('rtn', rtn);
  return rtn;

})

router.post('/save', async (req, res) => {

  

  const rtn = await storage.saveReservationData(req.body);
  console.log('rtn', rtn);
  return rtn;

})