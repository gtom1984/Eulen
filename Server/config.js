var config = new Object();

//Sample server config

//Express config
config.express_port = 8081;

//MySql configuration
config.mysql_server = 'localhost';
config.mysql_user = 'username';
config.mysql_pass = 'password';
config.mysql_port = 3309;
config.mysql_db = 'database';

//Snowflake configuration
config.dbid = 0;  //database ID
config.epoc = 1424005143979;  //launch year in seconds, offsets to allow for more IDs

//AWS configuration
config.aws_region = 'us-east-1';
config.aws_access = 'aws access';
config.aws_secret = 'aws secret';

//GCM configuration
config.google_api = 'Google Cloud Messenging API key';

//Eulen configuration
config.max_inbox = 5;
config.max_password = 32;
config.admin_account = 'Gmail account for server admin';

module.exports = config;