// create a new express object
var express = require('express');
var app = express();

// required modules
var bodyParser = require('body-parser');
var https = require('https');
var http = require('http');
var crypto = require('crypto');
var mysql = require('mysql');
var AWS = require('aws-sdk');
var intformat = require('biguint-format');

// load config file for Eulen
var config = require('./config.js');

// setup snowflake
var flake = require('flake-idgen');

// configure AWS
AWS.config.apiVersion = '2015-02-12';
AWS.config.update({accessKeyId: config.aws_access, secretAccessKey: config.aws_secret});
AWS.config.update({region: config.aws_region});

var cloudwatch = new AWS.CloudWatch();

// constants
const CONST_SYNC = "sync";
const CONST_FULL = "full";
const CONST_NO_DATA = "no data";
const CONST_ACCOUNT = "account";
const CONST_DATABASE = "database";
const CONST_EXISTS = "exists";
const CONST_INVALID = "invalid";
const CONST_ERROR = "error";
const CONST_SUCCESS = "success";
const CONST_NODE = "node";
const CONST_TRUE = "true";
const CONST_ER_DUP_ENTRY = "ER_DUP_ENTRY";
const CONST_COLLISION = "collision";
const CONST_AUTH = "auth";
const CONST_DATA = "data";
const CONST_END = "end";
const CONST_INVALID_TOKEN = "invalid_token";
const CONST_HTTP = "http";
const CONST_PORT = "port";

const CONST_REGISTER = "register";
const CONST_DELETE = "delete";
const CONST_INFO = "info";
const CONST_LIST = "list";
const CONST_SEND = "send";
const CONST_SEND_PHOTO = "send_photo";
const CONST_ERASE = "erase";

const CONST_COMMAND = "command";
const CONST_PASSWORD = "password";
const CONST_SNOWFLAKE_MON = "dec";
const CONST_HASH = "sha256";
const CONST_DIGEST = "base64";

const CONST_TO = "to";
const CONST_TOKEN = "token";
const CONST_DBMS = "dbms";
const CONST_EULEN_DB = "eulenNodeDB";
const chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

// use required express features
app.use(bodyParser.json({limit: '50mb'}));
app.use(bodyParser.urlencoded({ extended: true, limit: '50mb' }));
app.set(CONST_PORT, process.env.PORT || config.express_port); // setup port

// cloud watch alarm
function cloudWatch(metric, value) {
    var params = {
	MetricData: [ /* required */
	  {
	    MetricName: metric, /* required */
	    Dimensions: [
	      {
		Name: 'Source', /* required */
		Value: value /* required */
	      },
	      /* more items */
	    ],
	    Timestamp: new Date,
	    Unit: 'Count',
	    Value: 1
	  },
	  /* more items */
	],
        Namespace: CONST_EULEN_DB /* required */
      };
      cloudwatch.putMetricData(params, function(err, data) {
	//do nothing
      });
}

// catch random errors instead of crashing
process.on('uncaughtException', function (err) {
    cloudWatch(err,'exception');
});

var instanceID;

// remove table to reregister all nodes
function clearRegistrations() {
      // connect to MySQL
      var db = mysql.createConnection({
		host     : config.mysql_server,
		user     : config.mysql_user,
		password : config.mysql_pass,
		port 	   : config.mysql_port,
		database : config.mysql_db,
                dateStrings : CONST_TRUE
	      });

	      db.connect(function(err) {  //connect to database
		  if(err) {  //return error
		      	cloudWatch(CONST_ERROR, CONST_NODE);
		  } else {
                    db.query("DELETE FROM node", function(err, result) {
			      if(err) { //handle error
                                cloudWatch(CONST_ERROR, CONST_NODE);
			      } else { //else continue
				db.end(function(err) {
				  if(err) {
                                    cloudWatch(CONST_ERROR, CONST_NODE);
				  }
				}); //end connection
			      }
			    });
		  }
	      });
}

clearRegistrations();

// handle responses

app.get('/', function(request, response) {
      response.send('Just an Eulen node...nothing to see here.');
});

var snowflake;

// handle commands
app.post('/' + CONST_COMMAND, function(request, response) {
        var output = new Object();

	var outputJSON = function(output) {  //output JSON
	    this.output = output;
			 // Website you wish to allow to connect
	    response.setHeader('Access-Control-Allow-Origin', 'http://tomkowapp.com');

	    // Request methods you wish to allow
	    response.setHeader('Access-Control-Allow-Methods', 'POST');

	    // Request headers you wish to allow
	    response.setHeader('Access-Control-Allow-Headers', 'X-Requested-With,content-type');

	    // Set to true if you need the website to include cookies in the requests sent
	    // to the API (e.g. in case you use sessions)
	    response.setHeader('Access-Control-Allow-Credentials', true);

	    response.setHeader('Content-Type', 'application/json');
	    response.end(JSON.stringify(output));

	    connection.end(function(err) {
		      if(err) {

		      }
	    }); //end connection
	}

	function randomString() {  //generate simple random keys for device locking or device GCM confirmation
	    var length = config.max_password;
	    var result = '';
	    for (var i = length; i > 0; --i) result += chars[Math.round(Math.random() * (chars.length - 1))];
	    return result;
	}

	// GCM sender
	function gcm(reg_id,message) {
		this.reg_id = reg_id;
		this.message = message;

		var apikey = 'key=' + config.google_api;

		var body = {
		    "data": {
			"message": message,
		    },
		    "collapse_key": message,
		    "registration_ids": [reg_id]
		};

		var gcmJSON = JSON.stringify(body);

		var options = { //oauth options
			host: 'android.googleapis.com',
			port: '443',
			path: '/gcm/send',
			method: 'POST',
			headers: {
			    'Authorization': apikey,
			    'Content-Type': 'application/json',
			    'Content-Length': gcmJSON.length
			}
		};

		// request email from google
		var gcm_request = https.request(options, function(response){ // send GCM
		    response.on(CONST_DATA, function() {

		    });

		    response.on(CONST_END, function(){

		    });

		    response.on(CONST_ERROR, function(err){ // handle transmission errors

		    });
		});

		gcm_request.write(gcmJSON);
		gcm_request.end();
	}

	function hashPass(password, code) { // password hasher
	    this.password = password;
	    this.code = code;
	    var hash = null;

	    if(password != null && code != null) {
              hash = crypto.createHash(CONST_HASH).update(password + code).digest(CONST_DIGEST);
	    }

	    return hash;
	}

        var dbError = function() { // response for any DB related error post connection
          output.success = false;
          output.error = CONST_DATABASE;
          outputJSON(output);
        }


	function process(user, command) {  //command selector
	    this.user = user;
	    this.command = command;

	      switch(command) {
                case CONST_REGISTER:  // register new user
		  if(user.status == null && request.body.password != null && request.body.reg_id != null) {

		      user.code = randomString();
		      user.status = 1;

		      user.password = hashPass(request.body.password, user.code);

		      var snowflakeID = intformat(snowflake.next(), CONST_SNOWFLAKE_MON);

                      connection.query("INSERT INTO user (id, email, password, reg_id, code, status) VALUES (?, ?, ?, ?, ?, ?)", [snowflakeID, user.email, user.password, request.body.reg_id, user.code, user.status], function(err, result) {
			if (err) {
                          if(err.code == CONST_ER_DUP_ENTRY) {
                            cloudWatch(CONST_COLLISION,CONST_REGISTER);
			  }
			  dbError();
			} else {
			  //resturn user ID
			  output.success = true;
			  output.data = snowflakeID;

			  outputJSON(output);
			}
		      });
		  } else {
		    if(user.status == 1) {
		      output.success = false;
		      output.error = CONST_EXISTS;
		      outputJSON(output);

		    } else {
		      output.success = false;
		      output.error = CONST_INVALID;
		      outputJSON(output);
		    }
		  }
		break;

                case CONST_DELETE:  // delete user account
                  connection.query("DELETE FROM user WHERE id = ?", [user.id], function(err, results) {
		    if(err) { //handle error
                      dbError();
		    } else { // else continue
		      if(results.affectedRows > 0) {
			output.success = true;
			outputJSON(output);
		      } else {
			output.success = false;
                        output.error = CONST_ACCOUNT;
			outputJSON(output);
		      }
		    }
		  });
		break;

		case CONST_INFO:  // quick info for admin
		  var hashedAccount = crypto.createHash(CONST_HASH).update(config.admin_account).digest(CONST_DIGEST);

		  if(hashedAccount == user.email) {
                    connection.query("SELECT COUNT(*) as c FROM user", function(err, rows1) {
		      if(err) { //  handle errors
                        dbError();
		      } else { // else continue
			if(rows1.length > 0) {
                          connection.query("SELECT * FROM `node`", function(err, rows2) {
			      if(err) { // handle errors
				dbError();
			      } else { // else continue
				if(rows2.length > 0) {
				  output.success = true;
				  output.replying_node = instanceID;
				  output.all_nodes = rows2;
				  output.database_id = config.dbid;
				  output.user_count = rows1[0].c;
                                  output.snowflake_test = intformat(snowflake.next(), CONST_SNOWFLAKE_MON);
				  outputJSON(output);
				} else {
				  output.error = CONST_NO_DATA;
				  outputJSON(output);
				}
			      }
			  });
			} else {
			  output.error = CONST_NO_DATA;
			  outputJSON(output);
			}
		      }
		    });
		  } else {
		      output.success = false;
                      output.error = CONST_ACCOUNT;
		      outputJSON(output);
		  }
		break;

		case CONST_LIST: // list messages
                  connection.query("SELECT `id`, `time`, `key`, `data`, `type` FROM `message` WHERE `to` = ? ORDER BY `time` ASC", [user.id], function(err, rows) {
		      if(err) { // handle errors
                        dbError();
		      } else { // else continue
			if(rows.length > 0) {
			  output.success = true;
			  output.data = rows;
			  outputJSON(output);
			} else {
			  output.success = true;
			  output.data = null;
			  outputJSON(output);
			}
		      }
		    });

		break;

		case CONST_SEND: // send message
		  if(request.body.to != null && request.body.key != null) {
                    connection.query("SELECT COUNT(*) AS `count` FROM `message` WHERE `to` = ?", [request.body.to], function(err, rows) {
			  if(err) {
                            dbError();
			  } else {
			    if(rows[0].count <= config.max_inbox) { //check box size
                              connection.query("SELECT `reg_id` FROM `user` WHERE `id` = ?", [request.body.to], function(err, rows) { //get reg_id for user
				if(err) { // handle error
                                  dbError();
				} else { // else continue
				  if(rows.length > 0) { //if user is found
				      var reg_id = rows[0].reg_id;
				      var snowflakeID = intformat(snowflake.next(), CONST_SNOWFLAKE_MON);

                                      if(request.body.data != null) { // is a message
                                        connection.query("INSERT INTO `message` (`id`, `to`, `key`, `data`) VALUES (?, ?, ?, ?)", [snowflakeID, request.body.to, request.body.key, request.body.data], function(err, result) { //store message
                                          if (err) {
                                            if(err.code == CONST_ER_DUP_ENTRY) {
                                                cloudWatch(CONST_COLLISION, CONST_SEND);
                                            }
                                            dbError();
                                          } else {
                                            output.success = true;
                                            outputJSON(output);
                                            gcm(reg_id, CONST_SYNC);
                                          }
                                        });
                                      } else if (request.body.photo != null) { // is a photo
                                        connection.query("INSERT INTO `message` (`id`, `to`, `key`, `data`, `type`) VALUES (?, ?, ?, ?, ?)", [snowflakeID, request.body.to, request.body.key, request.body.photo, '1'], function(err, result) {    //store message
                                          if (err) {
                                            if(err.code == CONST_ER_DUP_ENTRY) {
                                              cloudWatch(CONST_COLLISION, CONST_SEND);
                                            }
                                            dbError();
                                          } else {
                                            output.success = true;
                                            outputJSON(output);
                                            gcm(reg_id, CONST_SYNC);
                                          }
                                        });
                                      }

				  } else { // if no user to send to
				    output.success = false;
				    output.error = CONST_TO;
				    outputJSON(output);
				  }
				}
			      });
			    } else {
			      output.success = false;
			      output.error = CONST_FULL;
			      outputJSON(output);
			      gcm(reg_id, CONST_FULL)
			    }
			  }
			});
		  } else {
		    output.success = false;
		    output.error = CONST_INVALID;
		    outputJSON(output);
		  }
		break;

		case CONST_ERASE: // erase message
		if(request.body.messageID != null) {
                  connection.query("DELETE FROM message WHERE `to` = ? AND `id` = ?", [user.id, request.body.messageID], function(err, results) {
		    if(err) { //handle errors
                      dbError();
		    } else { //else continue
		      if(results.affectedRows > 0) {
			output.success = true;
			outputJSON(output);
		      } else {
			output.success = false;
			outputJSON(output);
		      }
		    }
		  });
		} else {
		  output.success = false;
		  output.error = CONST_INVALID;
		  outputJSON(output);
		}

		break;

		default:
		  output.success = false;
		  output.error = CONST_COMMAND;
		  outputJSON(output);
		break;
	      }
	}

	function password(user) { // handle auth (some things require a device and others don't)
	    this.user = user;

	    if(request.body.command != null) {
	      var command = request.body.command;

	      if(command == CONST_DELETE || command == CONST_REGISTER || command == CONST_INFO) { // these functions do not require a device password
		process(user, command);
	      } else {
		if(user.password == hashPass(request.body.password, user.code)) { // these functions require a valid device password
		  if(user.status == 1) {
		      process(user, command);
		  } else {
		    output.success = false;
		    output.error = CONST_INVALID;
		    outputJSON(output);
		  }
		} else {  // report potential hacking to user if device password is wrong, this shouldn't happen because passwords are not entered by users
                  gcm(user.reg_id, CONST_PASSWORD);
		    output.success = false;
                    output.error = CONST_PASSWORD;
		    outputJSON(output);
		}
	      }
	    } else {
		output.success = false;
		output.error = CONST_COMMAND;
		outputJSON(output);
	    }
	}

	// query database for user account with the email
	function user(email) {   // attempt to lookup user based on email
		this.email = email;

                connection.query("SELECT * FROM user WHERE email = ?", [email], function(err, rows) {
		      if(err) { // handle error
                        dbError();
		      } else { // else continue
			    if(rows.length > 0) { // if user is found
				var user = rows[0];

				if(request.body.reg_id != null) { // update reg_id
				  var reg_id = request.body.reg_id;

				  if(reg_id !== user.reg_id) { //only if id has changed
                                    connection.query("UPDATE user SET reg_id = ? WHERE id = ?", [request.body.reg_id, user.id], function(err, rows) {
				      if(err) {
					// do nothing on error, this isn't super critical
				      } else {
					  user.reg_id = reg_id;
				      }
				    });
				  }
				}

				password(user); // pass user object to command module
			    } else { // return object with null user
				var user = new Object();
				user.email = email;
				user.status = null;

				password(user);
			    }
		      }
		});
	}

	// either fail or pass email to user lookup function
	function email(token) {  // convert token to email
		this.token = token;

		var options = { // oauth options
			host: 'www.googleapis.com',
			port: 443,
			path: '/oauth2/v1/tokeninfo?access_token=' + token
		};

		// request email from google
		https.get(options, function(response){ // validate token with Google
			var data = '';

			response.on(CONST_DATA, function(chunk){
				data += chunk;
			});

			response.on(CONST_END, function(){
				//var jsonStr = JSON.stringify(data);
				var result = JSON.parse(data);

                                if(result.error == CONST_INVALID_TOKEN) {  // handle token errors
				    var output = new Object();
				    output.success = false;
				    output.error = CONST_AUTH;

				    outputJSON(output);
				} else {
				    var hashedEmail = crypto.createHash(CONST_HASH).update(result.email).digest(CONST_DIGEST);
				    user(hashedEmail);  // pass email to user function
				}
			  });
                }).on(CONST_ERROR, function(e){ // handle transmission errors
			var output = new Object();
			output.success = false;
                        output.error = CONST_HTTP;
			outputJSON();
		});

	}

	// node reg
	function registerNode() {
	      function registerInstance(name, id) {
                connection.query("INSERT INTO node (id, name) VALUES (?, ?)", [id, name], function(err, result) {
				  if (err) {
				    if(err.code == CONST_ER_DUP_ENTRY) {
					queryInstanceDB(name);
				    }
				    cloudWatch(CONST_ERROR, CONST_NODE);
				  } else {
				    cloudWatch(CONST_SUCCESS, CONST_NODE);
				    getNodeID();

				  }
				});
	      }

	      function findLowestInt(myInstance, rows) { // obtain lowest number for node ID
		  var checkAgain = true;

		  var idIndex = [];
		  for(c = 0; c < rows.length; c++) {
		    var id = rows[c].id;
		    idIndex.push(id);
		  }


		  var counter = 0;
		  do {
		      if(idIndex.indexOf(counter) == -1) {
			registerInstance(myInstance, counter);
			checkAgain = false;
			break;
		      }
		      counter++;
		  }
		  while (checkAgain);
	      }

		function queryInstanceDB(myInstance) { // query instances in table
                  connection.query("SELECT * FROM node ORDER BY id ASC", function(err, rows) {
			      if(err) { // handle errors
                                  // internal, do nothing
			      } else { // else continue
				if(rows.length > 0) {
				      findLowestInt(myInstance, rows);
				} else {
				  registerInstance(myInstance, 0);
				}
			      }
			    });
		  }

		  // get local instance name options
		  var options = { // http options
			  host: '169.254.169.254',
			  port: 80,
			  path: '/latest/meta-data/instance-id'
		  };

		  // request instance name from local host
		  http.get(options, function(response){ // request instance name from local instance
			  var data = '';

			  response.on(CONST_DATA, function(chunk){
				  data += chunk;
			  });

			  response.on(CONST_END, function(){
				  instanceID = data;
				  queryInstanceDB(data);

			  });
			  }).on(CONST_ERROR, function(e){ // handle transmission errors
                              // internal, do nothing
			  });

		}
		// registration done
		function getNodeID() {
                  connection.query("SELECT id FROM node WHERE name = ?", [instanceID], function(err, rows) {
			      if(err) { // handle errors
					var output = new Object();
					output.success = false;
					output.error = CONST_NODE;

					outputJSON(output);

					registerNode();
			      } else { // else continue
				if(rows.length > 0) {
				    snowflake = new flake({ epoch: config.epoch, datacenter: config.dbid, worker: rows[0].id });

				    if (request.body.token != null) {  //ensure a token is submitted
					var token = request.body.token;
					email(token);  //pass token to auth module
				    } else {  //return error
					var output = new Object();
					output.success = false;
                                        output.error = CONST_TOKEN;
					outputJSON(output);
				    }
				} else {
				    registerNode();
				}
			      }
			});
		}

	// setup mysql database connection
	var connection = mysql.createConnection({
	  host     : config.mysql_server,
	  user     : config.mysql_user,
	  password : config.mysql_pass,
	  port 	   : config.mysql_port,
	  database : config.mysql_db,
	  dateStrings : CONST_TRUE,
          supportBigNumbers : CONST_TRUE,
          bigNumberStrings : CONST_TRUE
	});

	connection.connect(function(err) {  // connect to database
	    if(err) {  //return error
		var output = new Object();
		output.success = false;
                output.error = CONST_DBMS;
		outputJSON(output);
	    } else {
		getNodeID();
	    }
	});
});

app.listen(app.get(CONST_PORT)); // listen on port