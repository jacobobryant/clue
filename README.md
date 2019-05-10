# Clue

This is a command-line version of the board game Clue. It includes a simple AI. See the webapp branch for a half-baked web version.

## Usage

Watch three AIs play against each other:

    $ clj -m clue.play red ai green ai blue ai

Play as Miss Scarlet against a single AI (Mr. Green):

    $ clj -m clue.play red human green ai

The console will show an interface like this:

    Rooms: (0) Study, (1) Hall, (2) Lounge, (3) Dining Room, (4) Kitchen, (5) Ballroom, (6) Conservatory, (7) Billiard Room, (8) Library
    Your cards: Mrs. Peacock, Lead pipe, Professor Plum, Hall, Lounge, Billiard Room, Library, Colonel Mustard, Wrench

       a b c d e f g h i j k l m n o p q r s t u v w x
     1               -                 R              
     2               - -             - -              
     3               - -             - -              
     4               - -             - -              
     5   - - - - - 0 - 1             - -              
     6 - - - - - - - - -             - -              
     7             - - -             - - 2 - - - - -  
     8               - - - - 1 1 - - - - - - - - - - -
     9               8 -           - - - 3 - - - - -  
    10               - -           - -                
    11             - - -           - -                
    12   7 - 8 - - - - -           - -                
    13             - - -           - 3                
    14             - - -           - -                
    15             - - -           - -                
    16             7 - - - - - - - - - - - -          
    17             - G - 5 - - - - 5 - - - - - - - -  
    18   - - - - - - -                 - - - 4 - - - -
    19 - - - - - - - -                 - -            
    20           6 - 5                 5 -            
    21             - -                 - -            
    22             - -                 - -            
    23             - -                 - -            
    24               - - -         - - -              
    25                   -         -                  
    
    You rolled: 5
    Enter destination: 

You can enter coordinates like `q5` to move to a space on the board. To move to
a room, enter its number (e.g. `2` for the Lounge). The numbers on the board
show where the doors are.

## Progress

I'm currently converting this to a Datomic-backed web application. I'm planning to build
[a web framework](https://lispcast.com/clojure-needs-grow-boring-web-framework-boring-data-science/),
and this will be one of several example projects that I use to help me do that.

I'd also like to add a fancier AI using core.logic and/or machine learning.
