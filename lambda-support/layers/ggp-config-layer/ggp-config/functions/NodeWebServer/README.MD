
## create-table-reservation.json
```
{
    "TableName": "GoCheckInReservation",
    "KeySchema": [
      { "AttributeName": "listingId", "KeyType": "HASH" },
      { "AttributeName": "reservationCode", "KeyType": "RANGE" }
    ],
    "AttributeDefinitions": [
      { "AttributeName": "listingId", "AttributeType": "S" },
      { "AttributeName": "reservationCode", "AttributeType": "S" }
    ],
    "ProvisionedThroughput": {
      "ReadCapacityUnits": 5,
      "WriteCapacityUnits": 5
    }
}

```


## create-table-member.json
```
{
    "TableName": "GoCheckInMember",
    "KeySchema": [
      { "AttributeName": "reservationCode", "KeyType": "HASH" },
      { "AttributeName": "memberNo", "KeyType": "RANGE" }
    ],
    "AttributeDefinitions": [
      { "AttributeName": "reservationCode", "AttributeType": "S" },
      { "AttributeName": "memberNo", "AttributeType": "N" }
    ],
    "ProvisionedThroughput": {
      "ReadCapacityUnits": 5,
      "WriteCapacityUnits": 5
    }
}

```

```
$ aws dynamodb create-table --cli-input-json file://create-table-reservation.json --endpoint-url http://192.168.11.36:8080

$ aws dynamodb create-table --cli-input-json file://create-table-member.json --endpoint-url http://192.168.11.36:8080
```