## Project Structure
The project is structured as follows:
* the directory `frontend/ShareaPicture/` contains the Android application
* the directory `backend/` contains the GCP App Engine backend for the application
* the directory `backend/firebase-functions/` contains Firebase project
  definitions and code for the Firebase Functions that processes images
  as they are uploaded to Firebase Storage

## Building the App

To build the app, run
```
./deploy.sh
```

Once completed, the APK will be available in a file called `app-debug.apk`
in the the root directory of the repository. Also, the GCP App Engine
backend is deployed to Google Cloud. The script assumes you have
configured credentials and project for your Google Cloud CLI (gcloud
command).

## Deploying the Backend to Google Cloud Platform / Firebase
By default, tha app uses a backend that has already been deployed for the
project. If you, however, want to deploy your own backend, you can do it
by following these steps.

### Pre-requisites
To deploy the backend, you first need to create a new project in
Google Cloud Platform and import the project to Firebase.

You will also need the `gcloud` command line tool to be
[installed](https://cloud.google.com/sdk/downloads) and
[configured](https://cloud.google.com/sdk/docs/initializing) to use
the newly created Google Cloud Platform project.

Similarly, you need to install the [Firebase CLI](https://firebase.google.com/docs/cli/)
tool to setup your Firebase project.

### Credentials
Once you have created a project in GCP and Firebase, you will need
to import a few credentials for the frontend and backend to access
Firebase.

#### Android App Config for Firebase
To make your application able to talk to your Firebase project,
you need to do the following:

1. Go to the Firebase Console, open Project Settings and create a new app
2. Configure the new app by following the instructions in the Console
3. Download `google-services.json` and save the file to
   `frontend/ShareaPicture/app/google-services.json`.

#### Admin Account for GCP Backend and Firebase Cloud Functions
To make your backend able to manage your Firebase project, you
will need to download service account credentials for the Admin
SDK:

1. Go to the Firebase console, open Project Settings and choose the Service Accounts tab
2. Download the credentials for the Admin SDK and save the file to
   the following locations:
   * `backend/config/mcc-fall-2017-g14-firebase-adminsdk-kh3jq-e27f7b64f9.json`
   * `backend/firebase-functions/functions/config/mcc-fall-2017-g14-firebase-adminsdk-kh3jq-e27f7b64f9.json`

### Configure Project Name for the Backend
Once you have all the credentials for your new project, you will need to
configure the backend to talk to the correct project. To do that, change the
following configurations:

* In `backend/index.js`, change `databaseURL` and `storageBucket` to point to
  your project.
* In `firebase-functions/functions/index.js`, change `projectId`, `databaseURL`
  and `storageBucket` to point to your project

## Deploy the Backend
Once you have configured the credentials for your backend and changed it
to use your new project, you can deploy it. To do that, run the following
commands:

```
gcloud app deploy app.yaml
gcloud app deploy cron.yaml

firebase deploy --project <your-project-name> --only functions,database,storage
```

That's it. You can now build the app to use your custom backend.
