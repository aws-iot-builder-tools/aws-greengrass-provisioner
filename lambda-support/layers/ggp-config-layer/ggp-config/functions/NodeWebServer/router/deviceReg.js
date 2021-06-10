const Router = require('express-promise-router');

const router = new Router();

// export our router to be mounted by the parent application
module.exports = router

router.post('/deviceReg', async (req, res) => {

  console.log('req.body', req.body);
  
  const response = {
    "code":0,
    "message":"Good!" 
  };
  
  res.send(response);

})