// Command server holds a game of Morpho Match Duel for two devices.
//
// It listens on loopback only: an Android emulator reaches the Mac's
// loopback at 10.0.2.2, and nothing else needs to. A real headset would need
// the Mac's LAN address, which is a decision for later, not a default.
//
//	GET  /state?player=A          the game as A is told it
//	POST /flip?player=A&card=3    a flip; 409 with the reason when refused
//	POST /reset?seed=42           a new board, A to play
//	POST /reset?seed=42&hide=3s   the same, a mismatch held up for 3s (0.8s by default)
//	POST /bugs?on=a,b&off=all     the deliberate bugs
package main

import (
	"encoding/json"
	"flag"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"
)

func main() {
	addr := flag.String("addr", "127.0.0.1:8787", "where to listen")
	seed := flag.Int64("seed", 42, "the first board's seed")
	flag.Parse()
	log.Printf("Morpho Match Duel on %s, seed %d", *addr, *seed)
	log.Fatal(http.ListenAndServe(*addr, handler(NewDuo(*seed))))
}

func handler(d *Duo) http.Handler {
	mux := http.NewServeMux()
	reply := func(w http.ResponseWriter, code int, v any) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(code)
		_ = json.NewEncoder(w).Encode(v)
	}
	refuse := func(w http.ResponseWriter, code int, msg string) {
		reply(w, code, map[string]string{"error": msg})
	}
	list := func(s string) []string {
		var out []string
		for _, p := range strings.Split(s, ",") {
			if p = strings.TrimSpace(p); p != "" {
				out = append(out, p)
			}
		}
		return out
	}
	mux.HandleFunc("GET /state", func(w http.ResponseWriter, r *http.Request) {
		reply(w, http.StatusOK, d.ViewFor(r.URL.Query().Get("player")))
	})
	mux.HandleFunc("POST /flip", func(w http.ResponseWriter, r *http.Request) {
		p := r.URL.Query().Get("player")
		i, err := strconv.Atoi(r.URL.Query().Get("card"))
		if err != nil {
			refuse(w, http.StatusBadRequest, "card is a number from 0 to 15")
			return
		}
		if err := d.Flip(p, i); err != nil {
			refuse(w, http.StatusConflict, err.Error())
			return
		}
		reply(w, http.StatusOK, d.ViewFor(p))
	})
	mux.HandleFunc("POST /reset", func(w http.ResponseWriter, r *http.Request) {
		seed, err := strconv.ParseInt(r.URL.Query().Get("seed"), 10, 64)
		if err != nil {
			refuse(w, http.StatusBadRequest, "seed is a number")
			return
		}
		var hide time.Duration
		if h := r.URL.Query().Get("hide"); h != "" {
			if hide, err = time.ParseDuration(h); err != nil || hide <= 0 || hide > time.Minute {
				refuse(w, http.StatusBadRequest, "hide is a duration up to a minute, such as 3s")
				return
			}
		}
		d.Reset(seed, hide)
		reply(w, http.StatusOK, d.ViewFor("A"))
	})
	mux.HandleFunc("POST /bugs", func(w http.ResponseWriter, r *http.Request) {
		if err := d.SetBugs(list(r.URL.Query().Get("on")), list(r.URL.Query().Get("off"))); err != nil {
			refuse(w, http.StatusBadRequest, err.Error())
			return
		}
		reply(w, http.StatusOK, map[string]string{"ok": "bugs set"})
	})
	return mux
}
