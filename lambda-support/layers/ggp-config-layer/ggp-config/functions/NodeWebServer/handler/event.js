// event-handler

const LISTING_ID = process.env.LISTING_ID;
const AWS_IOT_THING_NAME = process.env.AWS_IOT_THING_NAME;

const storage = require('./storage');
const shadow = require('./shadow');

module.exports.syncReservation = async (event) => {

	console.log('syncReservation in: event:' + JSON.stringify(event));

	const getShadowResult = await shadow.getShadow({
	    thingName: AWS_IOT_THING_NAME,
	    shadowName: event.shadowName
	});

    const getReservationResult = await storage.getReservation({
        listingId: LISTING_ID,
        reservationCode: event.shadowName
    });

    let memberParams = [];
    if (getReservationResult) {
	    if (getReservationResult.version == getShadowResult.version) {
	    	console.log('ShadowReservation considered same as LocalReservation, nothing will be done');
	    	return;
	    } else {

	    	if (getShadowResult.state.desired.members.length < getReservationResult.members.length) {

	    		memberParams = getShadowResult.state.desired.members;

	    		//todo: clear GoCheckInMember for this reservation
	    		await storage.deleteMembers(getReservationResult.members);

	    		//todo: clear face info for this group    		
	    	} else {
				memberParams = getShadowResult.state.desired.members.filter((member, index) => {
					if (getReservationResult.members[index]) {
						return member.lastUpdateOn != getReservationResult.members[index].lastUpdateOn;	
					} else {
						return true;
					}
				});

	    		//todo: update face info for this group				
	    	}

	    }
    }

    await storage.saveReservation({
        reservation: getShadowResult.state.desired.reservation,
        members: memberParams,
        version: getShadowResult.version
    });

	console.log('syncReservation out');

	return;

};