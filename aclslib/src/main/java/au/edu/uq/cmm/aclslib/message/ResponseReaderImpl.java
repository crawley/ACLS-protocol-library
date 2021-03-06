/*
* Copyright 2012, CMM, University of Queensland.
*
* This file is part of AclsLib.
*
* AclsLib is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* AclsLib is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with AclsLib. If not, see <http://www.gnu.org/licenses/>.
*/

package au.edu.uq.cmm.aclslib.message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.MatchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is an ACLS response reader for versions 20.x and 30.x of the 
 * ACLS protocol.
 * 
 * @author scrawley
 */
public class ResponseReaderImpl extends AbstractReader implements ResponseReader {
    private static final Logger LOG = 
            LoggerFactory.getLogger(ResponseReaderImpl.class);
    
    public ResponseReaderImpl() {
        super(LOG, false);
    }

    public Response read(InputStream source) throws AclsException {
        BufferedReader br = new BufferedReader(new InputStreamReader(source));
        try {
            return readResponse(createLineScanner(br));
        } catch (IOException ex) {
            throw new AclsCommsException("IO error while reading response", ex);
        }
    }

    public Response readWithStatusLine(InputStream source) throws AclsException {
        BufferedReader br = new BufferedReader(new InputStreamReader(source));
        Scanner scanner;
        try {
            scanner = createLineScanner(br);
        } catch (IOException ex) {
            throw new AclsCommsException("IO error while reading status", ex);
        }
        String statusLine = scanner.nextLine();
        if (!statusLine.equals(AbstractMessage.ACCEPTED_IP_TAG)) {
            throw new ServerStatusException(statusLine);
        }
        try {
            scanner = createLineScanner(br);
        } catch (SocketTimeoutException ex) {
            throw new AclsNoResponseException("Timeout on response line", ex);
        } catch (IOException ex) {
            throw new AclsProtocolException("IO error while reading response message", ex);
        }
        return readResponse(scanner);
    }

    private Response readResponse(Scanner scanner) 
            throws AclsMessageSyntaxException, AclsNoResponseException {
        if (!scanner.hasNext()) {
            throw new AclsNoResponseException("Empty response message");
        }
        try {
            String command = scanner.next();
            expect(scanner, AbstractMessage.COMMAND_DELIMITER);
            ResponseType type = ResponseType.parse(command);
            switch (type) {
            case COMMAND_ERROR:
                return readCommandError(scanner);
            case LOGIN_ALLOWED: 
            case VIRTUAL_LOGIN_ALLOWED: 
            case NEW_VIRTUAL_LOGIN_ALLOWED: 
                return readLoginResponse(scanner, type);
            case ACCOUNT_ALLOWED:
            case VIRTUAL_ACCOUNT_ALLOWED:
            case NEW_VIRTUAL_ACCOUNT_ALLOWED:
                return readAccountResponse(scanner, type);
            case LOGIN_REFUSED: 
            case VIRTUAL_LOGIN_REFUSED:
            case NEW_VIRTUAL_LOGIN_REFUSED:
            case LOGOUT_REFUSED:
            case VIRTUAL_LOGOUT_REFUSED:
            case ACCOUNT_REFUSED:
            case VIRTUAL_ACCOUNT_REFUSED:
            case NEW_VIRTUAL_ACCOUNT_REFUSED:
            case NOTES_REFUSED:
            case FACILITY_REFUSED:
            case STAFF_LOGIN_REFUSED:
                return readRefused(scanner, type);
            case FACILITY_ALLOWED:
                return readFacility(scanner);
            case LOGOUT_ALLOWED:
            case VIRTUAL_LOGOUT_ALLOWED:
            case NOTES_ALLOWED:
            case STAFF_LOGIN_ALLOWED:
                return readAllowed(scanner, type);
            case PROJECT_YES:
            case TIMER_YES:
            case FULL_SCREEN_YES:
                return readYesNo(scanner, type, true);
            case PROJECT_NO:
            case TIMER_NO:
            case FULL_SCREEN_NO:
                return readYesNo(scanner, type, false);
            case USE_VIRTUAL:
                return readFacilityType(scanner);
            case FACILITY_COUNT:
                return readFacilityCount(scanner);
            case FACILITY_LIST:
                return readFacilityList(scanner);
            case SYSTEM_PASSWORD_NO:
            case SYSTEM_PASSWORD_YES:
                return readSystemPassword(scanner, type);
            case NET_DRIVE_NO:
            case NET_DRIVE_YES:
                return readNetDrive(scanner, type);
            default:
                throw new AssertionError("not implemented");
            }
        } catch (IllegalArgumentException ex) {
            throw new AclsMessageSyntaxException(ex.getMessage(), ex);
        } catch (NoSuchElementException ex) {
            throw new AclsMessageSyntaxException("Cannot parse response message", ex);
        }
    }

    private Response readNetDrive(Scanner scanner, ResponseType type) 
            throws AclsMessageSyntaxException {
        if (type == ResponseType.NET_DRIVE_NO) {
            return new NetDriveResponse();
        }
        if (scanner.findInLine("([^\\]]*)\\]([^\\[]*)\\[([^~]*)~([^|]*)") == null) {
            throw new AclsMessageSyntaxException("Cannot decode 'NetDrive' response");
        }
        MatchResult result = scanner.match();
        String driveName = result.group(1);
        String folderName = result.group(2);
        String accessName = result.group(3);
        String accessPassword = result.group(4);
        expectEnd(scanner);
        return new NetDriveResponse(driveName, folderName, accessName, accessPassword);
    }

    private Response readSystemPassword(Scanner scanner, ResponseType type) 
            throws AclsMessageSyntaxException {
        String password = null;
        if (type == ResponseType.SYSTEM_PASSWORD_YES) {
            expect(scanner, AbstractMessage.SYSTEM_PASSWORD_DELIMITER);
            password = nextSystemPassword(scanner);
            if (password.equals(AbstractMessage.DELIMITER)) {
                password = "";
            } 
            expectEnd(scanner);
        }
        return new SystemPasswordResponse(password);
    }

    private Response readFacilityType(Scanner scanner) 
            throws AclsMessageSyntaxException {
        expect(scanner, AbstractMessage.FACILITY_DELIMITER);
        String valueString = scanner.next();
        boolean value;
        if (valueString.equalsIgnoreCase(AbstractMessage.VMFL)) {
            value = true;
        } else if (valueString.equalsIgnoreCase(AbstractMessage.NO)) {
            value = false;
        } else {
            throw new AclsMessageSyntaxException(
                    "Expected 'vFML' or 'No' but got '" + valueString + "'");
        }
        expectEnd(scanner);
        return new YesNoResponse(ResponseType.USE_VIRTUAL, value);
    }

    private Response readFacilityCount(Scanner scanner) 
            throws AclsMessageSyntaxException {
        expect(scanner, AbstractMessage.FACILITY_DELIMITER);
        String countString = scanner.next();
        int count;
        try {
            count = Integer.parseInt(countString);
        } catch (NumberFormatException ex) {
            throw new AclsMessageSyntaxException(
                    "Invalid facility count '" + countString + "'");
        }
        expectEnd(scanner);
        return new FacilityCountResponse(count);
    }

    private Response readYesNo(Scanner scanner, ResponseType type, boolean b) 
            throws AclsMessageSyntaxException {
        expectEnd(scanner);
        return new YesNoResponse(type, b);
    }

    private Response readCommandError(Scanner scanner) 
            throws AclsMessageSyntaxException {
        expectEnd(scanner);
        return new CommandErrorResponse();
    }

    private Response readRefused(Scanner scanner, ResponseType type) 
            throws AclsMessageSyntaxException {
        expectEnd(scanner);
        return new RefusedResponse(type);
    }

    private Response readAllowed(Scanner scanner, ResponseType type) 
            throws AclsMessageSyntaxException {
        expectEnd(scanner);
        return new AllowedResponse(type);
    }

    private Response readFacility(Scanner scanner) 
            throws AclsMessageSyntaxException {
        expect(scanner, AbstractMessage.FACILITY_DELIMITER);
        String facility = nextFacility(scanner);
        expectEnd(scanner);
        return new FacilityNameResponse(facility);
    }

    private Response readFacilityList(Scanner scanner) 
            throws AclsMessageSyntaxException {
        expect(scanner, AbstractMessage.ACCOUNT_SEPARATOR);
        List<String> list = new ArrayList<String>();
        String token = nextSubfacility(scanner);
        while (!token.equals(AbstractMessage.DELIMITER)) {
            list.add(token);
            expect(scanner, AbstractMessage.ACCOUNT_SEPARATOR);
            token = scanner.next();
        }
        // For some reason, the server is holding the connection open ...
        // so we can't check that we get an EOF follwing the final
        // delimiter
        
        // expectEnd(scanner);
        return new FacilityListResponse(list);
    }

    private Response readLoginResponse(Scanner scanner, ResponseType type) 
            throws AclsMessageSyntaxException {
        String userName = nextName(scanner);
        expect(scanner, AbstractMessage.DELIMITER);
        String orgName = nextOrganization(scanner);
        expect(scanner, AbstractMessage.DELIMITER);
        String token = scanner.next();
        String timestamp = null;
        if (token.equals(AbstractMessage.TIME_DELIMITER)) {
            timestamp = nextTimestamp(scanner);
            expect(scanner, AbstractMessage.DELIMITER);
            token = scanner.next();
        }
        expect(token, AbstractMessage.ACCOUNT_DELIMITER);
        List<String> accounts = new ArrayList<String>();
        token = nextAccount(scanner);
        while (!token.equals(AbstractMessage.DELIMITER)) {
            accounts.add(token);
            expect(scanner, AbstractMessage.ACCOUNT_SEPARATOR);
            token = scanner.next();
        }
        expect(scanner, AbstractMessage.CERTIFICATE_DELIMITER);
        Certification certification = Certification.parse(scanner.next());
        token = scanner.next();
        boolean onsiteAssist = false;
        if (token.equals(AbstractMessage.ONSITE_ASSIST_DELIMITER)) {
            onsiteAssist = scanner.next().equalsIgnoreCase(AbstractMessage.YES);
        }
        expectEnd(scanner);
        return new LoginResponse(type, userName, orgName, timestamp,
                accounts, certification, onsiteAssist);
    }

    private Response readAccountResponse(Scanner scanner, ResponseType type) 
            throws AclsMessageSyntaxException {
        expect(scanner, AbstractMessage.TIME_DELIMITER);
        String timestamp = nextTimestamp(scanner);
        expectEnd(scanner);
        return new AccountResponse(type, timestamp);
    }
}
