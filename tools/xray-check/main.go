// Command xray-check validates configs produced by the app against the real
// xray-core loader, and pushes traffic through the "e2e-*" ones using local
// xray servers (see e2eServers).
//
//	go run . -assets <dir with geoip.dat/geosite.dat> <config.json>...
package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
	_ "github.com/xtls/xray-core/main/distro/all"
)

const e2eUUID = "b831381d-6324-4d53-ad4f-8cda48b30811"

// Recent xray blocks private destinations from proxy inbounds by default;
// the e2e target listens on loopback, so the test servers allow it explicitly.
const allowAll = `"outbounds":[{"protocol":"freedom","settings":{"finalRules":[{"action":"allow"}]}}]`

// Must match the e2e profiles in XrayConfigTest.
var e2eServers = []string{
	`{"inbounds":[{"listen":"127.0.0.1","port":18443,"protocol":"vless","settings":{"clients":[{"id":"` + e2eUUID + `"}],"decryption":"none"}}],` + allowAll + `}`,
	`{"inbounds":[{"listen":"127.0.0.1","port":18444,"protocol":"vmess","settings":{"clients":[{"id":"` + e2eUUID + `"}]}}],` + allowAll + `}`,
	`{"inbounds":[{"listen":"127.0.0.1","port":18445,"protocol":"vless","settings":{"clients":[{"id":"` + e2eUUID + `"}],"decryption":"none"},"streamSettings":{"network":"ws","wsSettings":{"path":"/ws"}}}],` + allowAll + `}`,
	`{"inbounds":[{"listen":"127.0.0.1","port":18388,"protocol":"shadowsocks","settings":{"method":"aes-128-gcm","password":"pw","network":"tcp,udp"}}],` + allowAll + `}`,
}

func main() {
	assets := flag.String("assets", "", "directory containing geoip.dat and geosite.dat")
	flag.Parse()
	if *assets != "" {
		os.Setenv("XRAY_LOCATION_ASSET", *assets)
	}

	failed := 0
	var e2e []string
	for _, path := range flag.Args() {
		if _, err := load(path); err != nil {
			fmt.Printf("FAIL %s: %v\n", filepath.Base(path), err)
			failed++
			continue
		}
		fmt.Printf("ok   %s\n", filepath.Base(path))
		if strings.HasPrefix(filepath.Base(path), "e2e-") {
			e2e = append(e2e, path)
		}
	}

	if len(e2e) > 0 {
		failed += runE2E(e2e)
	}
	if failed > 0 {
		fmt.Printf("%d failure(s)\n", failed)
		os.Exit(1)
	}
}

func load(path string) (*core.Config, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	return serial.LoadJSONConfig(f)
}

func runE2E(paths []string) int {
	for _, s := range e2eServers {
		inst, err := startJSON(s)
		if err != nil {
			fmt.Printf("FAIL starting e2e server: %v\n", err)
			return 1
		}
		defer inst.Close()
	}

	target := http.Server{Handler: http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		io.WriteString(w, "waveless")
	})}
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		panic(err)
	}
	go target.Serve(ln)
	defer target.Close()
	targetAddr := ln.Addr().(*net.TCPAddr)

	failed := 0
	for _, path := range paths {
		cfg, _ := load(path)
		inst, err := core.New(cfg)
		if err == nil {
			err = inst.Start()
		}
		if err == nil {
			err = fetch(inst, targetAddr)
			inst.Close()
		}
		if err != nil {
			fmt.Printf("FAIL e2e %s: %v\n", filepath.Base(path), err)
			failed++
		} else {
			fmt.Printf("ok   e2e %s: traffic flowed through the generated outbound\n", filepath.Base(path))
		}
	}
	return failed
}

func startJSON(js string) (*core.Instance, error) {
	cfg, err := serial.LoadJSONConfig(strings.NewReader(js))
	if err != nil {
		return nil, err
	}
	inst, err := core.New(cfg)
	if err != nil {
		return nil, err
	}
	return inst, inst.Start()
}

func fetch(inst *core.Instance, target *net.TCPAddr) error {
	client := http.Client{
		Timeout: 10 * time.Second,
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				dest, err := xnet.ParseDestination(network + ":" + addr)
				if err != nil {
					return nil, err
				}
				return core.Dial(ctx, inst, dest)
			},
		},
	}
	resp, err := client.Get(fmt.Sprintf("http://%s/", target))
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if string(body) != "waveless" {
		return fmt.Errorf("unexpected body %q", body)
	}
	return nil
}
