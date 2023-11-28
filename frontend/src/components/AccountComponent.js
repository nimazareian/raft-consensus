import React, {useEffect, useState} from 'react';
import Button from 'react-bootstrap/Button';
import styled from "styled-components";
import {RowWrapper} from "./SellComponent";
const AccountWrapper = styled.div`
  padding: 1.5rem;
  border: 2px solid grey;
  border-radius: 15px;
  grid-column: span 2;
`
const AccountComponent = () => {
    function stubFunction() {
        console.log("enter submit code here...")
    }

    return (
        <AccountWrapper>
            <h1>Account Balance:</h1>
            <Button onClick={stubFunction} variant="primary"> Add Funds </Button>
        </AccountWrapper>
    );
};

export default AccountComponent;
