// The Cloud Functions for Firebase SDK to create Cloud Functions and setup triggers.
const functions = require('firebase-functions');

// The Firebase Admin SDK to access the Firebase Realtime Database.
const admin = require('firebase-admin');
admin.initializeApp();

//This is for Google Sheets
const { google } = require('googleapis');
const peopleSheetId = <<<cencored>>>
const electionsInfoSheetId = <<<cencored>>>
const votesSheetId = <<<cencored>>>
const GoogleSheetsCredentials = <<<cencored>>>
const GoogleSheetsApiKey = <<<cencored>>>
const passwordForHttpsRequest = <<<cencored>>>
const passwordForApprovedPhones = <<<cencored>>>

//This is for phonelib
// Require `PhoneNumberFormat`.
const PNF = require('google-libphonenumber').PhoneNumberFormat;

// Get an instance of `PhoneNumberUtil`.
const phoneUtil = require('google-libphonenumber').PhoneNumberUtil.getInstance();

var SheetsService = google.sheets('v4');

exports.authorizePhones = functions.https.onRequest((req, res) => {
    authorize(function (authClient) {
        var spreadsheetId = electionsInfoSheetId;
        var range = 'ApprovedPhones!A2:H';

        authorizePhones(authClient, spreadsheetId, range, res);
    })
})

exports.setBunches = functions.https.onRequest((req, res) => {
    authorize(function (authClient) {
        var spreadsheetId = electionsInfoSheetId;
        var range = 'Bunches!A2:B';

        readBunches(authClient, spreadsheetId, range, res);
    })
})

exports.setPeople = functions.https.onRequest((req, res) => {
    authorize(function (authClient) {
        var spreadsheetId = peopleSheetId;
        var range = 'A2:Q';

        readPeople(authClient, spreadsheetId, range, res);
    })
})

exports.refreshVotes = functions.https.onRequest((req, res) => {
    authorize(function (authClient) {
        var spreadsheetId = votesSheetId;
        var range = 'Votes!A2:B';

        refreshVotes(authClient, spreadsheetId, range, res);
    })
})

exports.readVotes = functions.https.onRequest((req, res) => {
    if (req.query.passForEntry == null) {
        res.send("null argument");
        return;
    }
    else if (req.query.passForEntry == passwordForHttpsRequest) {
        return admin.database().ref("people").orderByChild("voteInfo/voted").equalTo(true).once('value').then(function (people) {
            if (!people.hasChildren()) {
                res.send("");
                return null;
            }
            var votesValues = "";
            people.forEach(function (person) {
                votesValues += person.child("id").val() + ',' + person.child("voteInfo/timeStamp").val() + ',' + person.child("voteInfo/taggedBy").val() + "<br />";
            });

            res.send(votesValues);
        });
    }
    else {
        res.send("No access");
    }
    return null;
})

exports.readVotesFromYishay = functions.https.onRequest((req, res) => {
    if (req.query.passForEntry == null) {
        res.send("null argument");
        return;
    }
    else if (req.query.passForEntry == passwordForHttpsRequest) {
        authorize(function (authClient) {
            var spreadsheetId = votesSheetId;
            var range = 'Other-Source!A2:A';

            readFromYishay(authClient, spreadsheetId, range, res);
        })
    }
    else {
        res.send("No access");
    }
})

exports.getRegisteredUsers = functions.https.onRequest((req, res) => {
    if (req.query.passForEntry == null) {
        res.send("null argument");
        return;
    }
    else if (req.query.passForEntry == passwordForHttpsRequest) {
        // List batch of users, 1000 at a time.

        var message = "";
        function listAllUsers(nextPageToken) {
            // List batch of users, 1000 at a time.
            return admin.auth().listUsers(1000, nextPageToken)
                .then(function (listUsersResult) {
                    listUsersResult.users.forEach(function (userRecord) {
                        var phoneNumber = userRecord.phoneNumber;
                        if (phoneNumber != null) {
                            phoneNumber = phoneNumber.substr(4);
                            phoneNumber = '0' + phoneNumber;
                            phoneNumber = phoneNumber.substr(0, 3) + '-' + phoneNumber.substr(3);
                        }
                        phoneNumber = "<b>" + phoneNumber + "</b>";
                        message += phoneNumber;
                    });
                    if (listUsersResult.pageToken) {
                        // List next batch of users.
                        listAllUsers(listUsersResult.pageToken)
                    }
                })
                .catch(function (error) {
                    console.log("Error listing users:", error);
                });
        }
        // Start listing users from the beginning, 1000 at a time.
        listAllUsers().then(() => {
            res.send(message);
        });
    }
    else {
        res.send("No access");
    }
})

exports.zeroTestingData = functions.https.onRequest((req, res) => {
    if (req.query.passForEntry == null) {
        res.send("null argument");
        return;
    }
    else if (req.query.passForEntry == passwordForHttpsRequest) {
        admin.database().ref('people').once('value').then(function (people) {
            var updates = {};
            people.forEach(function (person) {
                updates[person.key + '/contacted'] = null;
                updates[person.key + '/contactedTime'] = null;
                updates[person.key + '/colored'] = null;
                updates[person.key + '/voteInfo'] = null;
            })

            admin.database().ref('people').update(updates).then(() => {
                res.send("Resetted " + people.numChildren() + " people");
            })
        })
    }
    else {
        res.send("No access");
    }
})

exports.getColoredPeople = functions.https.onRequest((req, res) => {
    if (req.query.passForEntry == null) {
        res.send("null argument");
        return;
    }
    else if (req.query.passForEntry == passwordForHttpsRequest) {
        admin.database().ref('people').orderByChild('colored').equalTo(true).once('value').then(function (people) {
            var ids = [];
            people.forEach(function (person) {
                ids.push(person.child('id').val());
            })

            if (ids.length > 0)
                res.send(ids.toString());
            else
                res.send('');
        })
    }
    else {
        res.send("No access");
    }
})

function readFromYishay(authClient, spreadsheetId, range, res) {
    SheetsService.spreadsheets.values.get({
        spreadsheetId: spreadsheetId,
        range: range,
        auth: authClient.jwt,
        key: authClient.apiKey
    }, function (err, result) {
        if (err) {
            // Handle error
            console.log(err);
            return null;
        } else {
            var votesValues = "";

            var numRows = result.data.values ? result.data.values.length : 0;
            console.log(`${numRows} rows retrieved.`);

            const rows = result.data.values;

            rows.map((row) => {
                var id = parseInt(row[0], 10);
                votesValues += id + "<br />"
            });

            res.send(votesValues);
        }
    });
}

exports.readContactedPeople = functions.https.onRequest((req, res) => {
    if (req.query.passForEntry == null) {
        res.send("null argument");
        return;
    }
    else if (req.query.passForEntry == passwordForHttpsRequest) {
        admin.database().ref("people").orderByChild("contacted").equalTo(true).once('value').then(function (people) {
            if (!people.hasChildren()) {
                res.send("");
                return null;
            }
            var votesValues = "";
            people.forEach(function (person) {
                var time = person.child("contactedTime").exists() ? person.child("contactedTime").val() : "null";
                votesValues += person.child("id").val() + ',' + time + "<br />";
            });

            res.send(votesValues);
        });
    }
    else {
        res.send("No access");
    }
})

function authorize(callback) {
    var credentials = new google.auth.JWT(
        GoogleSheetsCredentials.client_email, null, GoogleSheetsCredentials.private_key,
        ['https://www.googleapis.com/auth/spreadsheets']);


    var authClient = {
        jwt: credentials,
        apiKey: GoogleSheetsApiKey
    }

    callback(authClient);
}

function authorizePhones(authClient, spreadsheetId, range, res) {
    SheetsService.spreadsheets.values.get({
        spreadsheetId: spreadsheetId,
        range: range,
        auth: authClient.jwt,
        key: authClient.apiKey
    }, function (err, result) {
        if (err) {
            // Handle error
            console.log(err);
            res.send("Error: " + err);
        } else {
            const phonesRef = admin.database().ref("approvedPhones");
            var users = {};
            var printList = [];

            var numRows = result.data.values ? result.data.values.length : 0;
            console.log(`${numRows} rows retrieved.`);

            var rowsUpdated = 0;

            //Update map
            const rows = result.data.values;
            var asyncTasks = [];
            rows.map((row) => {
                if (row[7] == passwordForApprovedPhones) {
                    rowsUpdated++;
                    var user = {};
                    //User Phone
                    var formatted = phoneUtil.parse(row[2].toString(), "IL");
                    var finalPhone = phoneUtil.format(formatted, PNF.E164);

                    //Validate non-duplication
                    if (users[finalPhone] != null) {
                        res.send("Error: phone number " + finalPhone + " is registered on 2 or more people.");
                        return null;
                    }

                    //User Name
                    user["name"] = row[1].toString();
                    user["role"] = roleToOrdinal(row[3].toString());
                    if (user.role == -1) { //Empty Role, invalid
                        res.send("Error: one of the users has an empty role");
                        return null;
                    }
                    else if (user.role == 0) { //Observer
                        if (row[5] == "") {
                            res.send("Error: one of the users' kalpis is empty");
                            return null;
                        }
                        user["roleValue"] = parseInt(row[5], 10);
                    }
                    else if (user.role == 1) { //Bunch
                        if (row[4] == "") {
                            res.send("Error: one of the users' bunches is empty");
                            return null;
                        }
                        user["roleValue"] = row[4].toString();
                        asyncTasks.push(admin.database().ref('bunches').orderByChild('name').equalTo(user.roleValue).once('value')
                            .then(function (snapshot) {
                                if (snapshot.hasChildren()) {
                                    snapshot.forEach(function (bunch) {
                                        user["roleValue_fb"] = bunch.key;
                                        user["kalpis"] = bunch.child("kalpis").val();
                                        return true;
                                    })
                                }
                                else {
                                    res.send("couldn't not find a specific user bunch: " + user.roleValue + "<br />please try to refresh the database");
                                    return false;
                                }
                            }));
                    }
                    else if (user.role == 2) { //Manager
                        //Nothing!
                    }
                    else { //Telephony
                        //Nothing!
                    }

                    users[finalPhone] = user;
                }
            });

            var message = `Successful operation.<br /> ${numRows} rows retreived.<br />${rowsUpdated} people were approved.`;
            if (numRows - rowsUpdated > 0)
                message += `<br />${numRows - rowsUpdated} people weren't set due to wrong password`;

            if (asyncTasks.length > 0)
                return Promise.all(asyncTasks).then(() => {
                    phonesRef.set(users).then(() => {
                        res.send(message);
                    })
                })
            else {
                phonesRef.set(users).then(() => {
                    res.send(message);
                })
            }
        }
    });
}
function roleToOrdinal(role) {
    if (role == "משקיף")
        return 0;
    else if (role == "מנהל אשכול")
        return 1;
    else if (role == "מטה בחירות")
        return 2;
    else if (role == "טלפוניה")
        return 3;
    else return -1;
}

function refreshVotes(authClient, spreadsheetId, range, res) {
    admin.database().ref("people").orderByChild("voteInfo/voted").equalTo(true).once('value').then(function (people) {
        if (!people.hasChildren()) {
            res.send("Empty votes");
            return null;
        }
        var votesValues = [];
        people.forEach(function (person) {
            votesValues.push([person.child("id").val(), person.child("voteInfo/timeStamp").val()]);
        });
        /* })
        admin.database().ref("votedPeople").once('value').then(function (votes) {
            if (!votes.exists()) {
                res.send("Empty votes");
                return null;
            }
            var votesValues = [];
            votes.forEach(function (vote) {
                votesValues.push([vote.key, vote.val()]);
            }); */

        SheetsService.spreadsheets.values.clear({
            spreadsheetId: spreadsheetId,
            range: range,
            auth: authClient.jwt,
            key: authClient.apiKey
        }, function (err, result) {
            if (err) {
                // Handle error
                console.log(err);
                res.send("Error: " + err);
                return null;
            }
            else {
                SheetsService.spreadsheets.values.append({
                    spreadsheetId: spreadsheetId,
                    range: range,
                    valueInputOption: 'RAW',
                    resource: {
                        values: votesValues
                    },
                    auth: authClient.jwt,
                    key: authClient.apiKey
                }, function (err, result) {
                    if (err) {
                        // Handle error
                        console.log(err);
                        res.send("Error: " + err);
                    } else {
                        res.send(`Updated the votes table. Total votes: ${votesValues.length}`)
                    }
                });
            }
        });
    })
}

function readBunches(authClient, spreadsheetId, range, res) {
    SheetsService.spreadsheets.values.get({
        spreadsheetId: spreadsheetId,
        range: range,
        auth: authClient.jwt,
        key: authClient.apiKey
    }, function (err, result) {
        if (err) {
            // Handle error
            console.log(err);
            res.send("Error: " + err);
        } else {
            const numRows = result.data.values ? result.data.values.length : 0;
            console.log(`${numRows} rows retrieved.`);

            const bunchesRef = admin.database().ref("bunches");
            const kalpisRef = admin.database().ref("kalpis");

            //Update map
            var bunches = {};
            var kalpis = {};
            var printList = [];

            const rows = result.data.values;
            var bunchName = numRows > 0 ? rows[0][0] : "";
            var bunchKey = numRows > 0 ? bunchesRef.push().key : "";
            printList.push(rows.length + " were set<br /><br />");
            var kalpisOfBunch = {};
            var kalpisOfMasterBunch = {};
            var counter = 0;
            rows.map((row) => {
                counter++;
                if (row[0] != "" && row[0] != bunchName) {
                    var bunch = {};
                    bunch["name"] = bunchName;
                    bunch["kalpis"] = kalpisOfBunch;
                    bunches[bunchKey] = bunch;

                    kalpisOfBunch = {};
                    bunchName = row[0];
                    bunchKey = bunchesRef.push().key;
                }

                var kalpiKey = kalpisRef.push().key;
                var kalpiNum = parseInt(row[1].toString(), 10);
                kalpisOfBunch['k' + kalpiNum] = true; //'k' is used for Firebase Database Rules bug
                kalpisOfMasterBunch['k' + kalpiNum] = true;

                var kalpi = { number: kalpiNum, bunch: bunchKey };
                kalpis[kalpiKey] = kalpi;

                printList.push("[" + bunchName + ", " + row[1] + "]<br />");

                if (counter == numRows) {
                    var bunch = {};
                    bunch["name"] = bunchName;
                    bunch["kalpis"] = kalpisOfBunch;
                    bunches[bunchKey] = bunch;
                }

            });

            //Create the "Master Bunch" for managers
            var bunch = {};
            bunch["name"] = "MasterBunch";
            bunch["kalpis"] = kalpisOfMasterBunch;
            bunches["masterBunch"] = bunch;

            Promise.all([bunchesRef.set(bunches), kalpisRef.set(kalpis)])
                .then(() => {
                    res.send(printList.toString());
                });
        }
    });
}

function readPeople(authClient, spreadsheetId, range, res) {

    //while (toContinue) {
    SheetsService.spreadsheets.values.get({
        spreadsheetId: spreadsheetId,
        //range: currentRange == 100 ? "A2:Q100" : `A${currentRange + 1}:Q${currentRange + rangeJumps}`,
        range: range,
        auth: authClient.jwt,
        key: authClient.apiKey
    }, function (err, result) {
        if (err) {
            // Handle error
            console.log(err);
            res.send("Error: " + err);
        } else {
            const peopleRef = admin.database().ref("people");
            var people = {};
            var printList = [];

            var numRows = result.data.values ? result.data.values.length : 0;
            console.log(`${numRows} rows retrieved.`);
            printList.push(`${numRows} people were set<br />`)

            const rows = result.data.values;
            rows.map((row) => {
                var person = {
                    id: parseInt(row[0], 10),
                    lastName: row[1],
                    firstName: row[2],
                    kalpi: parseInt(row[5], 10),
                    id_inside_kalpi: parseInt(row[16], 10)
                }
                people[peopleRef.push().key] = person;
                //printList.push(`[${person.id}, ${person.firstName}, ${person.lastName}]<br />`);
            });

            peopleRef.set(people).then(() => {
                res.send("Updated successfully");
            });
        }
    });
}