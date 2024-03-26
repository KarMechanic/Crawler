<h1> Basic Java Crawler </h1>

<h3> Requirements </h3>

'crawler program' that:
- based on a 'start page', for example: https://en.wikipedia.org/wiki/Open-source_intelligence
- crawls other web pages
- determines the most important words on these pages
- in a certain time frame (e.g. five minutes)
- and a maximum number of 'steps' from the start page.

<h3> Development Considerations </h3>

<h4> 1. Rescheduling of Tasks on exception </h4>

in the 'Crawler' class, if we got an IOException from for example, JSoup failing to connect to the URL then
it would be good to add a retry mechanism into the crawler so that the task is rescheduled. 

A possible implementation might include:
- maxRetries variable for the number of attempts before we stop
- A 'delay' variable which would correspond to the number of seconds before retrying the task
- A method of scaling the delay so that we don't overwhelm the link we want to access.

The 'scheduledExecutorService' is currently only being used to schedule the shutdown task after the 
maximum crawl time allowed however, it could also be made responsible for rescheduling the failed 
tasks after a delay.

<h4> 2. Testing Suite </h4>

If this application was meant for any sort of production-level development then an extensive
testing suite would be introduced to make sure everything is working as intended.

<h4> 3. More robust logging </h4>

Proper logging to monitor the status of the program.

<h4> 4. GUI </h4>

The current method of showing the results is quite sub-par, given time a proper interface for interacting with the crawler
and showcasing the results in a cleaner way would be a lot better.

<h4> 5. Checking 'robots.txt' </h4>

The crawler currently does not check each websites robot.txt which should be done to respect common web crawling convention

