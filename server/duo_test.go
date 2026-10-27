package main

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// newTestDuo is a duo whose mismatches are turned back when the test says.
func newTestDuo(t *testing.T, bugs ...string) (*Duo, func()) {
	t.Helper()
	d := NewDuo(42)
	var pending []func()
	d.after = func(_ time.Duration, f func()) { pending = append(pending, f) }
	if err := d.SetBugs(bugs, nil); err != nil {
		t.Fatal(err)
	}
	return d, func() {
		for _, f := range pending {
			f()
		}
		pending = nil
	}
}

func pairOf(d *Duo, s string) (int, int) {
	var at []int
	for i, x := range d.deck {
		if x == s {
			at = append(at, i)
		}
	}
	return at[0], at[1]
}

func mismatch(d *Duo) (int, int) {
	a, _ := pairOf(d, "morpho")
	b, _ := pairOf(d, "atlas")
	return a, b
}

// tests-duo/duo.sh plays seed 42 from this deal; it is pinned so a change
// here breaks this test rather than the script.
func TestSeed42IsTheDealTheScriptPlays(t *testing.T) {
	d := NewDuo(42)
	want := "owl,emerald,glasswing,emerald,monarch,atlas,swallowtail,monarch,glasswing,peacock,owl,atlas,swallowtail,morpho,morpho,peacock"
	if got := strings.Join(d.deck, ","); got != want {
		t.Errorf("seed 42 deals\n%s\nwant\n%s", got, want)
	}
}

func TestAMatchKeepsTheTurnAndScores(t *testing.T) {
	d, _ := newTestDuo(t)
	a, b := pairOf(d, "monarch")
	if err := d.Flip("A", a); err != nil {
		t.Fatal(err)
	}
	if err := d.Flip("A", b); err != nil {
		t.Fatal(err)
	}
	v := d.ViewFor("B")
	if v.Turn != "A" || v.Scores["A"] != 1 || v.Cards[a].State != "matched" || v.Cards[a].Species != "monarch" {
		t.Errorf("after A's match B sees turn %s, scores %v, card %+v", v.Turn, v.Scores, v.Cards[a])
	}
}

func TestAMismatchHandsTheTurnOver(t *testing.T) {
	d, settle := newTestDuo(t)
	a, b := mismatch(d)
	_ = d.Flip("A", a)
	_ = d.Flip("A", b)
	if v := d.ViewFor("B"); v.Cards[a].State != "up" || v.Turn != "A" {
		t.Fatalf("B should see A's two cards up, A still to play: %+v %s", v.Cards[a], v.Turn)
	}
	if err := d.Flip("A", 5); !errors.Is(err, errBusy) {
		t.Errorf("a third card while two are up: %v", err)
	}
	settle()
	v := d.ViewFor("A")
	if v.Turn != "B" || v.Cards[a].State != "down" || v.Cards[a].Species != "" {
		t.Errorf("after the mismatch A sees turn %s, card %+v", v.Turn, v.Cards[a])
	}
}

func TestAFlipOutOfTurnIsRefused(t *testing.T) {
	d, _ := newTestDuo(t)
	if err := d.Flip("B", 0); !errors.Is(err, errNotYourTurn) {
		t.Errorf("B on A's turn: %v", err)
	}
	if err := d.Flip("C", 0); !errors.Is(err, errNoPlayer) {
		t.Errorf("a third player: %v", err)
	}
}

func TestEachBugIsCaught(t *testing.T) {
	d, _ := newTestDuo(t, "out-of-turn")
	if err := d.Flip("B", 0); err != nil {
		t.Errorf("out-of-turn should accept B's flip, got %v", err)
	}

	d, settle := newTestDuo(t, "turn-stuck")
	a, b := mismatch(d)
	_ = d.Flip("A", a)
	_ = d.Flip("A", b)
	settle()
	if v := d.ViewFor("A"); v.Turn != "A" {
		t.Errorf("turn-stuck should keep A's turn, got %s", v.Turn)
	}

	d, _ = newTestDuo(t, "score-swap")
	a, b = pairOf(d, "monarch")
	_ = d.Flip("A", a)
	_ = d.Flip("A", b)
	if va, vb := d.ViewFor("A"), d.ViewFor("B"); va.Scores["A"] != 1 || vb.Scores["A"] != 0 || vb.Scores["B"] != 1 {
		t.Errorf("score-swap: A sees %v, B sees %v", va.Scores, vb.Scores)
	}

	d, _ = newTestDuo(t, "match-hands-over")
	a, b = pairOf(d, "monarch")
	_ = d.Flip("A", a)
	_ = d.Flip("A", b)
	if v := d.ViewFor("A"); v.Turn != "B" {
		t.Errorf("match-hands-over should hand the turn to B, got %s", v.Turn)
	}

	if err := d.SetBugs([]string{"no-such"}, nil); err == nil {
		t.Error("an unknown bug was accepted")
	}
}

func TestAStaleMismatchDoesNotHideANewGame(t *testing.T) {
	d, settle := newTestDuo(t)
	a, b := mismatch(d)
	_ = d.Flip("A", a)
	_ = d.Flip("A", b)
	d.Reset(7, 0)
	c, _ := pairOf(d, "owl")
	_ = d.Flip("A", c)
	settle()
	if v := d.ViewFor("A"); v.Turn != "A" || v.Cards[c].State != "up" {
		t.Errorf("the old game's timer touched the new one: turn %s, card %+v", v.Turn, v.Cards[c])
	}
}

// tests-duo/duo-play.sh asks for a long hide so both screens can be read
// while a mismatch is up; the next reset without one is a game people play.
func TestResetSetsTheHideAndGivesTheDefaultBack(t *testing.T) {
	d := NewDuo(42)
	srv := httptest.NewServer(handler(d))
	defer srv.Close()
	post := func(q string) int {
		r, err := http.Post(srv.URL+"/reset?"+q, "", nil)
		if err != nil {
			t.Fatal(err)
		}
		r.Body.Close()
		return r.StatusCode
	}
	if c := post("seed=42&hide=3s"); c != 200 || d.hideAfter != 3*time.Second {
		t.Errorf("hide=3s: status %d, hide %v", c, d.hideAfter)
	}
	if c := post("seed=42"); c != 200 || d.hideAfter != defaultHide {
		t.Errorf("no hide: status %d, hide %v", c, d.hideAfter)
	}
	for _, bad := range []string{"3", "-1s", "2m", "soon"} {
		if c := post("seed=42&hide=" + bad); c != http.StatusBadRequest {
			t.Errorf("hide=%s: status %d, want 400", bad, c)
		}
	}
}
