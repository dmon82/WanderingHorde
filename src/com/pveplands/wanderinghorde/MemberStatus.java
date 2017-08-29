package com.pveplands.wanderinghorde;

public enum MemberStatus {
    Idle,
    WalkingToWaypoint,
    GettingNextWaypoint,
    CampAtNight,
    
    /* exclusive to anchors */
    WaitingForSatellites,
    WaitingOneTurn,
    
    /* exclusive to satellites */
    Scattering,
    Scattered,
    GatherAroundAnchor,
    
    /* combat test */
    WalkingToTarget
}
