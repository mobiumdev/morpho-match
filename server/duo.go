package main

import (
	"errors"
	"math/rand"
	"sync"
	"time"
)

// The species, in the app's order, by id; a deal shuffles their pairs.
var species = []string{"morpho", "monarch", "swallowtail", "glasswing", "owl", "peacock", "atlas", "emerald"}

// bugIDs are the deliberate bugs the duo can be switched into.
var bugIDs = map[string]string{
	"out-of-turn":      "a flip is accepted from the player whose turn it is not",
	"turn-stuck":       "a mismatch does not hand the turn over",
	"score-swap":       "player B is sent the two scores swapped",
	"match-hands-over": "a match hands the turn over instead of keeping it",
}

// Card is one card as a player is told it: its species only while up or matched.
type Card struct {
	State   string `json:"state"` // down, up or matched
	Species string `json:"species,omitempty"`
}

// View is the game as one player is told it.
type View struct {
	Version int            `json:"version"`
	Seed    int64          `json:"seed"`
	Turn    string         `json:"turn"`
	Scores  map[string]int `json:"scores"`
	Cards   []Card         `json:"cards"`
	Over    bool           `json:"over"`
}

// Duo is one game of two players, A and B, kept here and nowhere else: the
// devices show it, and a turn is the server's to hand over.
type Duo struct {
	mu      sync.Mutex
	seed    int64
	deck    []string
	up      []int
	matched map[int]string // card -> the player who took it
	scores  map[string]int
	turn    string
	version int
	bugs    map[string]bool
	// hideAfter is how long a mismatched pair stays up; after is the clock
	// that turns it back, replaced in tests.
	hideAfter time.Duration
	after     func(time.Duration, func())
}

// defaultHide is how long a mismatch stays up in a game people play.
const defaultHide = 800 * time.Millisecond

func NewDuo(seed int64) *Duo {
	d := &Duo{bugs: map[string]bool{}, after: func(t time.Duration, f func()) {
		time.AfterFunc(t, f)
	}}
	d.Reset(seed, 0)
	return d
}

// Reset deals a new board from seed, A to play. A mismatch stays up for
// hide, or the default when hide is 0: a test that reads two screens while
// the pair is up asks for longer, and a game reset without asking gets the
// default back.
func (d *Duo) Reset(seed int64, hide time.Duration) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if hide <= 0 {
		hide = defaultHide
	}
	d.hideAfter = hide
	deck := append(append([]string{}, species...), species...)
	r := rand.New(rand.NewSource(seed))
	r.Shuffle(len(deck), func(i, j int) { deck[i], deck[j] = deck[j], deck[i] })
	d.seed, d.deck, d.up, d.turn = seed, deck, nil, "A"
	d.matched = map[int]string{}
	d.scores = map[string]int{"A": 0, "B": 0}
	d.version++
}

var (
	errNotYourTurn = errors.New("not your turn")
	errBusy        = errors.New("two cards are up")
	errTaken       = errors.New("that card is already up or taken")
	errNoCard      = errors.New("no such card")
	errNoPlayer    = errors.New("the players are A and B")
)

var other = map[string]string{"A": "B", "B": "A"}

// Flip turns card i over for player p.
func (d *Duo) Flip(p string, i int) error {
	d.mu.Lock()
	defer d.mu.Unlock()
	switch {
	case p != "A" && p != "B":
		return errNoPlayer
	case i < 0 || i >= len(d.deck):
		return errNoCard
	case p != d.turn && !d.bugs["out-of-turn"]:
		return errNotYourTurn
	case len(d.up) == 2:
		return errBusy
	}
	if _, ok := d.matched[i]; ok || (len(d.up) == 1 && d.up[0] == i) {
		return errTaken
	}
	d.up = append(d.up, i)
	d.version++
	if len(d.up) < 2 {
		return nil
	}
	a, b := d.up[0], d.up[1]
	if d.deck[a] == d.deck[b] {
		d.matched[a], d.matched[b] = p, p
		d.scores[p]++
		d.up = nil
		if d.bugs["match-hands-over"] {
			d.turn = other[d.turn]
		}
		return nil // a match keeps the turn
	}
	v := d.version
	d.after(d.hideAfter, func() { d.settle(v) })
	return nil
}

// settle turns a mismatched pair back and hands the turn over, unless the
// game moved on since.
func (d *Duo) settle(v int) {
	d.mu.Lock()
	defer d.mu.Unlock()
	if d.version != v || len(d.up) != 2 {
		return
	}
	d.up = nil
	if !d.bugs["turn-stuck"] {
		d.turn = other[d.turn]
	}
	d.version++
}

// ViewFor is the game as player p is told it.
func (d *Duo) ViewFor(p string) View {
	d.mu.Lock()
	defer d.mu.Unlock()
	v := View{Version: d.version, Seed: d.seed, Turn: d.turn, Over: len(d.matched) == len(d.deck),
		Scores: map[string]int{"A": d.scores["A"], "B": d.scores["B"]}}
	if p == "B" && d.bugs["score-swap"] {
		v.Scores["A"], v.Scores["B"] = d.scores["B"], d.scores["A"]
	}
	for i, s := range d.deck {
		c := Card{State: "down"}
		if _, ok := d.matched[i]; ok {
			c = Card{State: "matched", Species: s}
		} else if len(d.up) > 0 && (d.up[0] == i || len(d.up) == 2 && d.up[1] == i) {
			c = Card{State: "up", Species: s}
		}
		v.Cards = append(v.Cards, c)
	}
	return v
}

// SetBugs turns bugs on and off; an unknown id refuses the whole change.
func (d *Duo) SetBugs(on, off []string) error {
	for _, id := range append(append([]string{}, on...), off...) {
		if _, ok := bugIDs[id]; !ok && id != "all" {
			return errors.New("not a bug: " + id)
		}
	}
	d.mu.Lock()
	defer d.mu.Unlock()
	for _, id := range off {
		if id == "all" {
			d.bugs = map[string]bool{}
			continue
		}
		delete(d.bugs, id)
	}
	for _, id := range on {
		d.bugs[id] = true
	}
	return nil
}
