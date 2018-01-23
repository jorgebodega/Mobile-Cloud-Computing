const express = require('express')
const morgan = require('morgan')
const PORT = process.env.PORT || 8080

const firebase = require('firebase-admin')
const serviceAccount = require('./config/mcc-fall-2017-g14-firebase-adminsdk-kh3jq-e27f7b64f9');

firebase.initializeApp({
  credential: firebase.credential.cert(serviceAccount),
  databaseURL: 'https://mcc-fall-2017-g14.firebaseio.com',
  storageBucket: 'mcc-fall-2017-g14.appspot.com'
})

const app = express()
app.use(morgan('combined'))

function deleteGroup(group) {
  const groupId = group.key
  const bucket = firebase.storage().bucket()

  console.log(`Deleting group ${groupId}`)

  return firebase.database().ref().child(`group_members/${groupId}`).once('value')
    .then(snapshot => {
      const userUpdates = {}
      Object.keys(snapshot.val() || {}).forEach(uid => {
        userUpdates[`/users/${uid}/groups/${groupId}`] = null
      })

      return Promise.all([
        firebase.database().ref().child(`images/${groupId}`).remove().catch(err => {
          console.log('Failed to remove image metadata', groupId, err)
        }),
        firebase.database().ref().child(`tokens/${groupId}`).remove().catch(err => {
          console.log('Failed to remove group tokens', groupId, err)
        }),
        firebase.database().ref().child(`groups/${groupId}`).remove().catch(err => {
          console.log('Failed to remove group metadata', groupId, err)
        }),
        firebase.database().ref().child(`group_members/${groupId}`).remove().catch(err => {
          console.log('Failed to remove group member list', groupId, err)
        }),
        bucket.deleteFiles({ prefix: `images/${groupId}`, force: true }).catch(err => {
          console.log('Failed to remove some images', groupId, err)
        }),
        firebase.database().ref().update(userUpdates).catch(err => {
          console.log('Failed to remove some user->group mappings', groupId, err)
        })
      ])
    }).catch(err => {
      console.log('Some error handler failed!', err)
    })
}

app.get('/scheduled/cleanup', function (req, res) {
  if (!req.get('X-Appengine-Cron')) {
    res.status(401).json({ error: 'Unauthorized' })
    return
  }

  // Remove a group 2 hours from expiry the earliest
  const end = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()
  firebase.database().ref()
    .child('groups')
    .orderByChild('expiry')
    .endAt(end)
    .once('value')
    .then(groups => {
      const tasks = []
      groups.forEach(group => {
        tasks.push(deleteGroup(group))
      })

      return Promise.all(tasks)
    }).then(_ => {
      res.status(200).end()
    }).catch(_ => {
      console.log('Error occurred', _)
      res.status(200).end()
    })
})

app.use(function (req, res, next) {
  const token = req.get('Authorization')
  if (!token) {
    res.status(401).json({ error: 'Authentication token missing' })
    return
  }

  firebase.auth().verifyIdToken(token).then(function (data) {
    req.user = data
    next()
  }).catch(function (err) {
    res.status(401).json({ error: 'Invalid authentication token' })
  })
})

// Parse application/json request bodies as JSON
app.use(express.json())

app.post('/groups', function (req, res) {
  const ts = Date.parse(req.body.expiry)
  if (isNaN(ts)) {
    return res.status(400).json({ error: 'Could not parse expiry date' });
  }

  const expiry = new Date(ts).toISOString()
  const created = new Date().toISOString()
  const name = req.body.name

  const group = firebase.database().ref().child('groups').push().key;
  const updates = {}
  updates[`/groups/${group}`] = {name, expiry, created, admin: req.user.uid}
  updates[`/users/${req.user.uid}/groups/${group}`] = true
  updates[`/tokens/${group}`] = require('crypto').randomBytes(32).toString('hex')
  firebase.auth().getUser(req.user.uid).then(userInfo => {
    // Add the username to the group_members object of a group for easier
    // access
    updates[`/group_members/${group}/${req.user.uid}`] = userInfo.displayName
    return firebase.database().ref().update(updates)
  }).then(result => {
    res.status(200).json({ group_id: group, expiry })
  }).catch(err => {
    console.log('Failed to persist changes to Firebase', err)
    res.status(500).json({ error: 'Temporary error. Try again later' })
  })

})

// Verify the join token of the incoming request. Ff valid,
// add the current user to the given group and generate a new
// join token
app.post('/groups/:group_id/join', function (req, res) {
  const group = req.params.group_id
  const token = req.body.token

  const updates = {}

  firebase.database().ref().child('tokens').child(group)
    .once('value')
    .then(ref => {
      // Check if the token is correct
      if (ref.val() !== token) {
        const err = new Error('Group join token invalid')
        err.name = 'InvalidJoinToken'
        throw err
      }

      // Token is correct
      updates[`/users/${req.user.uid}/groups/${group}`] = true
      updates[`/tokens/${group}`] = require('crypto').randomBytes(32).toString('hex')

      return firebase.auth().getUser(req.user.uid)
    }).then(userInfo => {
      // Add the username to the group_members object of a group for easier
      // access
      updates[`/group_members/${group}/${req.user.uid}`] = userInfo.displayName
      return firebase.database().ref().update(updates)
    }).then(_ => {
      return firebase.database().ref().child('groups').child(group).once('value')
    }).then(result => {
      const groupInfo = result.val()
      res.status(200).json({ group_id: group, expiry: groupInfo.expiry })
    }).catch(err => {
      if (err.name === 'InvalidJoinToken') {
        console.log('Invalid token attempted')
        res.status(400).json({ error: 'Invalid token' })
      } else {
        console.log('Group join failed', err)
        res.status(500).json({ error: 'Temporary error. Try again later' })
      }
    })
})

app.post('/groups/:group_id/leave', function (req, res) {
  firebase.database().ref()
    .child('groups').child(req.params.group_id).child('admin')
    .once('value')
    .then(ref => {
      if (ref.val() === req.user.uid) {
        const err = new Error('Admin cannot leave the group')
        err.name = 'AdminCannotLeave'
        throw err
      }

      // Not the admin. Delete all traces between the user
      // and the group
      const updates = {}
      updates[`/users/${req.user.uid}/groups/${req.params.group_id}`] = null
      updates[`/group_members/${req.params.group_id}/${req.user.uid}`] = null
      return firebase.database().ref().update(updates)
    }).then(result => {
      res.status(200).json({})
    }).catch(err => {
      if (err.name === 'AdminCannotLeave') {
        res.status(400).json({ error: 'Admin cannot leave the group' })
      } else {
        console.log('Leave failed', err)
        res.status(500).json({ error: 'Temporary error. Try again later' })
      }
    })
})

app.post('/groups/:group_id/delete', function (req, res) {
  const groupRef = firebase.database().ref().child('groups').child(req.params.group_id)
  groupRef.child('admin')
    .once('value')
    .then(ref => {
      if (ref.val() !== req.user.uid) {
        const err = new Error('Only admin can delete the group')
        err.name = 'NotAnAdmin'
        throw err
      }

      return deleteGroup(groupRef)
    }).then(_ => {
      res.status(200).json({})
    }).catch(err => {
      if (err.name === 'NotAnAdmin') {
        res.status(401).json({ error: 'Your not the admin of this group' })
      } else {
        console.log('Group delete failed', err)
        res.status(500).json({ error: 'Temporary error. Try again later' })
      }
    })
})

app.all('*', function (req, res) {
  res.status(404).json({ error: 'Nothing to see here. Move along!' })
})

app.listen(PORT, function () {
  console.log('Application listening on port ' + PORT)
})
