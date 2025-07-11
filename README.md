# Fabi-1
**Fabi** - discord bot written in Java using JDA library.  
Functions:
- Server moderation and sync blacklists;
- Custom voice channels;
- Simple verification with join roles;
- Ticketing.

## Installation
Requirements: Java JDK/JRE 21

Install OpenJDK21:
[here](https://green.cloud/docs/how-to-install-java-jdk-21-or-openjdk-21-on-debian-12/)
#### From source:
1. `git clone https://github.com/FireatomYT/Fabi-1`
2. `cd Fabi-1`
3. `./gradlew build`
4. Finally `sh linux-start.sh` or `windows-start.bat`

#### Watchdog service:
Available [here](https://github.com/FiLe-group/VOTL-watchdog).

### Configuration
After first bot launch, folders `data` and `logs` will be created at .jar file location (or other specified location).  
Inside folder `data` file `config.json` must be configured with data as stated below.

#### Config file (data/config.json):
```json
{
    "bot-token": "",
    "owner-id": "owner's ID",
    "dev-servers": [
        "dev server's IDs"
    ],
    "webhook": "link to webhook, if you want to receive ERROR level logs"
}
```
