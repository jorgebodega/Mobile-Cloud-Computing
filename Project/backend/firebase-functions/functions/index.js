// @ts-check
const functions = require('firebase-functions');
const spawn = require('child-process-promise').spawn;
const fs = require('fs');
const os = require('os');
const path = require('path');
const tmp = require('tmp-promise')

const serviceAccount = require('./config/mcc-fall-2017-g14-firebase-adminsdk-kh3jq-e27f7b64f9');
const firebase = require('firebase-admin')

const fbConfig = {
  projectId: "mcc-fall-2017-g14",
  keyfileName: 'config/mcc-fall-2017-g14-firebase-adminsdk-kh3jq-e27f7b64f9.json'
};

const vision = require('@google-cloud/vision')(fbConfig);

firebase.initializeApp({
  credential: firebase.credential.cert(serviceAccount),
  databaseURL: 'https://mcc-fall-2017-g14.firebaseio.com',
  storageBucket: 'mcc-fall-2017-g14.appspot.com'
})

const db = firebase.database();

const ORIGINAL_IMAGE_NAME_REGEXP = new RegExp("images/(.+)/image/(.+)/original.jpg")

function isOriginalImageCreateEvent(event) {
    const { bucket, name, resourceState, metageneration } = event.data

    if (!ORIGINAL_IMAGE_NAME_REGEXP.test(name)) {
        console.log('Ignoring changes to non-original group photo', name)
        return false
    }

    if (resourceState === 'not_exists') {
        console.log('Ignoring delete event for', bucket, name)
        return false
    }

    if (resourceState === 'exists' && metageneration > 1) {
        console.log('Ignoring change event to existing image', bucket, name)
        return false
    }

    return true
}

function getImageDetails(event) {
    const { bucket, name } = event.data
    const matches = ORIGINAL_IMAGE_NAME_REGEXP.exec(name)
    const groupId = matches[1]
    const imageId = matches[2]
    return { groupId, imageId, url: `gs://${bucket}/${name}`, name }
}

exports.callVision = functions.storage.object().onChange(event => {
  console.log('Received event', event)
  if (!isOriginalImageCreateEvent(event)) {
      return Promise.resolve()
  }

  const { groupId, imageId, url } = getImageDetails(event)
  return Promise.resolve()
      .then(() => {
      let visionReq = {
          "image": {
              "source": {
                  "imageUri": url
              }
          },
          "features": [
              {
                  "type": "FACE_DETECTION"
              },
          ]
      }
      return vision.annotate(visionReq);
    })
    .then(([visionData]) => {
      console.log('got vision data: ', visionData[0]);
      return db.ref(`images/${groupId}/${imageId}`).update({ faces: visionData[0].faceAnnotations.length })
    })
    .then(() => {
      console.log(`Parsed vision annotation and wrote to Firebase`);
    })
    .catch(err => {
      console.log('error', err)
    });
});

function generateResizedImage(event, width, height, imageName) {

  const { groupId, imageId, name } = getImageDetails(event)
  const resizedName = name.replace('original.jpg', imageName)
  let tmpOriginal, tmpResized
  return Promise.all([
    tmp.file(),
    tmp.file()
  ]).then(([original, resized]) => {
    tmpOriginal = original
    tmpResized = resized

    // Download the image to local temp file
    return firebase.storage().bucket().file(name).download({ destination: tmpOriginal.path })
  }).then(_ => {
    // Resize the local file to correct resolution
    return spawn('convert', [tmpOriginal.path, '-resize', `${width}x${height}>`, tmpResized.path])
  }).then(_ => {
    // Upload it back to Storage
    return firebase.storage().bucket().upload(tmpResized.path, {
      destination: resizedName,
      metadata: {
        contentType: 'image/jpeg',
      }
    })
  }).then(_ => {
    console.log(`Successfully resized ${name} to ${resizedName}`)
    tmpOriginal.cleanup()
    tmpResized.cleanup()
  }).catch(err => {
    console.log('Something went wrong', err)
    if (tmpOriginal) tmpOriginal.cleanup()
    if (tmpResized) tmpResized.cleanup()
  })
}

exports.resizeToHighResolution = functions.storage.object().onChange(event => {
  console.log('Received event', event)
  if (!isOriginalImageCreateEvent(event)) {
      return Promise.resolve()
  }

  return generateResizedImage(event, 1280, 960, 'high.jpg')
})

exports.resizeToLowResolution = functions.storage.object().onChange(event => {
  console.log('Received event', event)
  if (!isOriginalImageCreateEvent(event)) {
      return Promise.resolve()
  }

  return generateResizedImage(event, 640, 480, 'low.jpg')
})
