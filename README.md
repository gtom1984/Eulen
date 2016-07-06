# Eulen

Server:

The server requires node.js and mysql.
Running nginx with SSL is highly recommended for a production deployment.

The server is configured by editing config.js
A Google cloud messaging API key and Amazon Web Services account is required for push messaging and monitoring.
The mysql server should be setup with the included schema.sql file



Client:
Create a google-services.json file and add it to client/eulen.  This file is not included because it will contain a production API key.
You can obtain your own GCM key at https://console.cloud.google.com/apis/api/googlecloudmessaging/